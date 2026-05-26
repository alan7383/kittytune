package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.library.LibraryItem
import com.alananasss.kittytune.ui.library.LibraryViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FabSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val fab by prefs.bottomMenuFabFlow().collectAsState(initial = prefs.getBottomMenuFab())

    val libraryViewModel: LibraryViewModel = viewModel()
    val haptic = LocalHapticFeedback.current
    
    // Make sure data is loaded
    LaunchedEffect(Unit) {
        libraryViewModel.loadData()
    }

    val playlists = libraryViewModel.displayedItems.filterIsInstance<LibraryItem.PlaylistItem>().map { it.playlist }.filter { !it.isAlbum }
    
    // Default system actions
    val systemOptions = mutableListOf(
        "profile" to stringResource(R.string.pref_bottom_menu_fab_profile),
        "settings" to stringResource(R.string.pref_bottom_menu_fab_settings),
        "achievements" to stringResource(R.string.achievements_title),
        "stats" to stringResource(R.string.pref_bottom_menu_fab_stats),
        "liked" to stringResource(R.string.lib_liked_tracks),
        "downloads" to stringResource(R.string.lib_downloads)
    ).apply {
        if (prefs.getLocalMediaEnabled()) {
            add("local" to stringResource(R.string.lib_local_media))
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_bottom_menu_fab),
        onBackClick = onBackClick
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = padding.calculateBottomPadding() + 150.dp,
                top = 8.dp
            )
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_bottom_menu_fab)) // Actions
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        systemOptions.forEachIndexed { index, (key, title) ->
                            SettingsItem(
                                shape = getSettingsShape(systemOptions.size, index),
                                title = title,
                                subtitle = null,
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    prefs.setBottomMenuFab(key) 
                                },
                                icon = if (fab == key) Icons.Rounded.Check else null
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_bottom_menu_fab_playlist)) // Custom playlists
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.fab_settings_no_playlists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                    )
                }
            } else {
                items(playlists) { playlist ->
                    val isRadioShortcut = playlist.permalinkUrl?.startsWith("yt_radio:") == true
                    val navKey = if (isRadioShortcut) {
                        "playlist:" + android.net.Uri.encode(playlist.permalinkUrl!!)
                    } else {
                        if (playlist.id < 0) "playlist:local_playlist:${playlist.id}" else "playlist:${playlist.id}"
                    }
                    val isSelected = fab == navKey

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                prefs.setBottomMenuFab(navKey) 
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            val authorText = playlist.user?.username ?: ""
                            val finalSubtitle = if (isRadioShortcut) stringResource(R.string.fab_settings_radio) else stringResource(R.string.lib_playlists) + " • $authorText"
                            Text(
                                text = finalSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.fab_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
