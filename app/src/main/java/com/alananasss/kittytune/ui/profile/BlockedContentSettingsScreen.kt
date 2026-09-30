package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.local.AiDetectionWindow
import com.alananasss.kittytune.data.local.BlockedContent
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.*
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlockedContentSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val modelDownloadState by com.alananasss.kittytune.audio.ai.AiDetectionManager.downloadState.collectAsState()
    var aiAutoSkip by remember { mutableStateOf(prefs.aiAutoSkip) }
    var aiAutoBlock by remember { mutableStateOf(prefs.aiAutoBlock) }
    var aiSpareFavorites by remember { mutableStateOf(prefs.aiSpareFavorites) }
    var aiThreshold by remember { mutableStateOf(prefs.aiScoreThreshold) }
    var aiShowBadge by remember { mutableStateOf(prefs.aiShowBadge) }
    var aiShowHumanBadge by remember { mutableStateOf(prefs.aiShowHumanBadge) }
    var aiWindow by remember { mutableStateOf(prefs.aiDetectionWindow) }
    var showAiWindowDialog by remember { mutableStateOf(false) }
    var showDownloadPromptDialog by remember { mutableStateOf(false) }
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    val blockedTracks by (BlockManager.observeBlockedTracks() ?: emptyFlow())
        .collectAsState(initial = emptyList())
    val blockedArtists by (BlockManager.observeBlockedArtists() ?: emptyFlow())
        .collectAsState(initial = emptyList())
    var trackToUnblock by remember { mutableStateOf<BlockedContent?>(null) }
    var artistToUnblock by remember { mutableStateOf<BlockedContent?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (trackToUnblock != null) {
        AlertDialog(
            onDismissRequest = { trackToUnblock = null },
            icon = { Icon(Icons.Rounded.VisibilityOff, null) },
            title = { Text(stringResource(R.string.block_unblock_track_title)) },
            text = { Text(stringResource(R.string.block_unblock_track_body, trackToUnblock!!.trackTitle ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        BlockManager.unblockTrack(trackToUnblock!!.trackId!!)
                        trackToUnblock = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.block_restore)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { trackToUnblock = null },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (artistToUnblock != null) {
        AlertDialog(
            onDismissRequest = { artistToUnblock = null },
            icon = { Icon(Icons.Rounded.PersonAdd, null) },
            title = { Text(stringResource(R.string.block_unblock_artist_title)) },
            text = { Text(stringResource(R.string.block_unblock_artist_body, artistToUnblock!!.artistName ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        BlockManager.unblockArtist(artistToUnblock!!.artistId!!)
                        artistToUnblock = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.block_restore)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { artistToUnblock = null },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.block_clear_all_title)) },
            text = { Text(stringResource(R.string.block_clear_all_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        blockedTracks.forEach { it.trackId?.let { id -> BlockManager.unblockTrack(id) } }
                        blockedArtists.forEach { it.artistId?.let { id -> BlockManager.unblockArtist(id) } }
                        showClearAllDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.block_clear_all_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showDownloadPromptDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadPromptDialog = false },
            icon = { Icon(Icons.Rounded.CloudDownload, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.block_model_download_prompt_title)) },
            text = { Text(stringResource(R.string.block_model_download_prompt_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDownloadPromptDialog = false
                        com.alananasss.kittytune.audio.ai.AiDetectionManager.downloadModel(context)
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.block_model_download_btn)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDownloadPromptDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showDeleteModelDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteModelDialog = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(stringResource(R.string.block_model_delete_title)) },
            text = { Text(stringResource(R.string.block_model_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        com.alananasss.kittytune.audio.ai.AiDetectionManager.deleteModel(context)
                        aiAutoSkip = false
                        prefs.aiAutoSkip = false
                        aiAutoBlock = false
                        prefs.aiAutoBlock = false
                        showDeleteModelDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.block_model_delete_btn)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteModelDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showAiWindowDialog) {
        AlertDialog(
            onDismissRequest = { showAiWindowDialog = false },
            icon = { Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = stringResource(R.string.block_ai_window_dialog_title),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (option in AiDetectionWindow.entries) {
                        val isSelected = aiWindow == option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    aiWindow = option
                                    prefs.aiDetectionWindow = option
                                    showAiWindowDialog = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected)
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else
                                null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(option.titleRes),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(option.descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAiWindowDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_content_filter_title),
        onBackClick = onBackClick,
        actions = {
            if (blockedTracks.isNotEmpty() || blockedArtists.isNotEmpty()) {
                IconButton(onClick = { showClearAllDialog = true }) {
                    Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.block_clear_all_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 180.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                SettingsGroupTitle(stringResource(R.string.block_section_ai))
            }
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    AiModelDownloadCard(
                        downloadState = modelDownloadState,
                        onDownload = { com.alananasss.kittytune.audio.ai.AiDetectionManager.downloadModel(context) },
                        onDelete = { showDeleteModelDialog = true }
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Block 1: Auto-skip group with animated corner radius
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val autoSkipBottomRadius by animateDpAsState(
                            targetValue = if (aiAutoSkip) 4.dp else 24.dp,
                            label = "AutoSkipCornerAnim"
                        )

                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = autoSkipBottomRadius,
                                bottomEnd = autoSkipBottomRadius
                            ),
                            title = stringResource(R.string.block_ai_auto_skip),
                            subtitle = stringResource(R.string.block_ai_auto_skip_sub),
                            icon = Icons.Rounded.SkipNext,
                            hasSwitch = true,
                            switchState = aiAutoSkip,
                            onSwitchChange = { enabled ->
                                if (enabled && !com.alananasss.kittytune.audio.ai.AiDetectionManager.isModelReady(context)) {
                                    showDownloadPromptDialog = true
                                } else {
                                    aiAutoSkip = enabled
                                    prefs.aiAutoSkip = enabled
                                    if (!enabled) {
                                        aiAutoBlock = false
                                        prefs.aiAutoBlock = false
                                    }
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = aiAutoSkip,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SettingsItem(
                                    shape = RoundedCornerShape(4.dp),
                                    title = stringResource(R.string.block_ai_auto_block),
                                    subtitle = stringResource(R.string.block_ai_auto_block_sub),
                                    icon = Icons.Rounded.Block,
                                    hasSwitch = true,
                                    switchState = aiAutoBlock,
                                    onSwitchChange = {
                                        aiAutoBlock = it
                                        prefs.aiAutoBlock = it
                                    }
                                )
                                SettingsItem(
                                    shape = RoundedCornerShape(4.dp),
                                    title = stringResource(R.string.block_ai_spare_favorites),
                                    subtitle = stringResource(R.string.block_ai_spare_favorites_sub),
                                    icon = Icons.Rounded.Favorite,
                                    hasSwitch = true,
                                    switchState = aiSpareFavorites,
                                    onSwitchChange = {
                                        aiSpareFavorites = it
                                        prefs.aiSpareFavorites = it
                                    }
                                )
                                val pct = (aiThreshold * 100).toInt()
                                SettingsItem(
                                    shape = RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 4.dp,
                                        bottomStart = 24.dp,
                                        bottomEnd = 24.dp
                                    ),
                                    title = stringResource(R.string.block_ai_threshold),
                                    subtitle = stringResource(R.string.block_ai_threshold_sub, pct),
                                    icon = Icons.Rounded.Tune,
                                    hasSlider = true,
                                    sliderValue = aiThreshold,
                                    sliderRange = 0.5f..0.95f,
                                    onSliderChange = {
                                        aiThreshold = it
                                        prefs.aiScoreThreshold = it
                                    }
                                )
                            }
                        }
                    }

                    // Block 2: AI Analysis window selector
                    SettingsItem(
                        shape = RoundedCornerShape(24.dp),
                        title = stringResource(R.string.block_ai_window_title),
                        subtitle = stringResource(aiWindow.titleRes),
                        icon = Icons.Rounded.Speed,
                        onClick = { showAiWindowDialog = true }
                    )

                    // Block 3: Badges group with animated corner radius
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val badgeBottomRadius by animateDpAsState(
                            targetValue = if (aiShowBadge) 4.dp else 24.dp,
                            label = "BadgeCornerAnim"
                        )

                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = badgeBottomRadius,
                                bottomEnd = badgeBottomRadius
                            ),
                            title = stringResource(R.string.pref_ai_show_badge),
                            subtitle = stringResource(R.string.pref_ai_show_badge_sub),
                            icon = Icons.Rounded.SmartToy,
                            hasSwitch = true,
                            switchState = aiShowBadge,
                            onSwitchChange = {
                                aiShowBadge = it
                                prefs.aiShowBadge = it
                            }
                        )

                        AnimatedVisibility(
                            visible = aiShowBadge,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 24.dp,
                                    bottomEnd = 24.dp
                                ),
                                title = stringResource(R.string.pref_ai_show_human_badge),
                                subtitle = stringResource(R.string.pref_ai_show_human_badge_sub),
                                icon = Icons.Rounded.VerifiedUser,
                                hasSwitch = true,
                                switchState = aiShowHumanBadge,
                                onSwitchChange = {
                                    aiShowHumanBadge = it
                                    prefs.aiShowHumanBadge = it
                                }
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SettingsGroupTitle(
                    stringResource(R.string.block_section_tracks, blockedTracks.size)
                )
            }

            if (blockedTracks.isEmpty()) {
                item {
                    EmptyBlockedItem(
                        icon = Icons.Rounded.VisibilityOff,
                        message = stringResource(R.string.block_empty_tracks)
                    )
                }
            } else {
                items(blockedTracks, key = { it.id }) { item ->
                    BlockedContentRow(
                        artworkUrl = item.trackArtworkUrl,
                        title = item.trackTitle ?: stringResource(R.string.untitled_track),
                        subtitle = if (item.reason == BlockManager.REASON_AI_GENERATED)
                            stringResource(R.string.block_reason_ai)
                        else
                            stringResource(R.string.block_reason_manual),
                        subtitleColor = if (item.reason == BlockManager.REASON_AI_GENERATED)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        onRestoreClick = { trackToUnblock = item }
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SettingsGroupTitle(
                    stringResource(R.string.block_section_artists, blockedArtists.size)
                )
            }

            if (blockedArtists.isEmpty()) {
                item {
                    EmptyBlockedItem(
                        icon = Icons.Rounded.PersonOff,
                        message = stringResource(R.string.block_empty_artists)
                    )
                }
            } else {
                items(blockedArtists, key = { it.id }) { item ->
                    BlockedContentRow(
                        artworkUrl = item.artistAvatarUrl,
                        title = item.artistName ?: stringResource(R.string.unknown_artist),
                        subtitle = if (item.reason == BlockManager.REASON_AI_GENERATED)
                            stringResource(R.string.block_reason_ai)
                        else
                            stringResource(R.string.block_reason_manual),
                        subtitleColor = if (item.reason == BlockManager.REASON_AI_GENERATED)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        onRestoreClick = { artistToUnblock = item }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedContentRow(
    artworkUrl: String?,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    onRestoreClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor
                )
            }

            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onRestoreClick,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    Icons.Rounded.RestoreFromTrash,
                    stringResource(R.string.block_restore),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EmptyBlockedItem(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiModelDownloadCard(
    downloadState: com.alananasss.kittytune.audio.ai.AiDetectionManager.ModelDownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_huggingface),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.block_model_card_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.block_model_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(R.string.block_model_desc_what),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.block_model_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            when (downloadState) {
                is com.alananasss.kittytune.audio.ai.AiDetectionManager.ModelDownloadState.Ready -> {
                    FilledTonalButton(
                        onClick = onDelete,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.block_model_delete_btn),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                is com.alananasss.kittytune.audio.ai.AiDetectionManager.ModelDownloadState.Downloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.block_model_downloading, (downloadState.progress * 100).toInt()),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${downloadState.downloadedBytes / (1024 * 1024)} / 17 MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                    }
                }
                is com.alananasss.kittytune.audio.ai.AiDetectionManager.ModelDownloadState.NotDownloaded,
                is com.alananasss.kittytune.audio.ai.AiDetectionManager.ModelDownloadState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (downloadState is com.alananasss.kittytune.audio.ai.AiDetectionManager.ModelDownloadState.Error) {
                            Text(
                                text = "Error: ${downloadState.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = onDownload,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.block_model_download_btn),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Declarative settings definitions for Content Filtering and AI Music Detection.
 * Adding a setting here automatically makes it searchable and interactive across the app.
 */
internal val ContentFilterSettingDefinitions: List<SettingDefinition> = listOf(
    SettingDefinition.Navigation(
        id = "content_filter_page",
        titleRes = R.string.pref_content_filter_title,
        subtitleRes = R.string.pref_content_filter_subtitle,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.Block
    ),
    SettingDefinition.Navigation(
        id = "ai_detection_section",
        titleRes = R.string.block_section_ai,
        subtitleRes = R.string.block_model_card_title,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.SmartToy,
        keywords = listOf("suno", "udio", "artifactnet")
    ),
    SettingDefinition.Switch(
        id = "ai_auto_skip",
        titleRes = R.string.block_ai_auto_skip,
        subtitleRes = R.string.block_ai_auto_skip_sub,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.SkipNext,
        get = { it.aiAutoSkip },
        set = { prefs, value ->
            prefs.aiAutoSkip = value
            if (!value) prefs.aiAutoBlock = false
        },
        onToggleIntercept = { context, target, fallbackSet, navigate ->
            if (target && !com.alananasss.kittytune.audio.ai.AiDetectionManager.isModelReady(context)) {
                navigate("content_filter_settings")
            } else {
                fallbackSet(target)
            }
        }
    ),
    SettingDefinition.Switch(
        id = "ai_auto_block",
        titleRes = R.string.block_ai_auto_block,
        subtitleRes = R.string.block_ai_auto_block_sub,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.Block,
        get = { it.aiAutoBlock },
        set = { prefs, value -> prefs.aiAutoBlock = value }
    ),
    SettingDefinition.Switch(
        id = "ai_spare_favorites",
        titleRes = R.string.block_ai_spare_favorites,
        subtitleRes = R.string.block_ai_spare_favorites_sub,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.Favorite,
        get = { it.aiSpareFavorites },
        set = { prefs, value -> prefs.aiSpareFavorites = value }
    ),
    SettingDefinition.Action(
        id = "ai_threshold",
        titleRes = R.string.block_ai_threshold,
        subtitleRes = R.string.block_ai_threshold_sub,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.Tune,
        formatTitleArgs = { arrayOf((it.aiScoreThreshold * 100).toInt()) },
        formatSubtitleArgs = { arrayOf((it.aiScoreThreshold * 100).toInt()) }
    ),
    SettingDefinition.Switch(
        id = "ai_show_badge",
        titleRes = R.string.pref_ai_show_badge,
        subtitleRes = R.string.pref_ai_show_badge_sub,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.SmartToy,
        get = { it.aiShowBadge },
        set = { prefs, value -> prefs.aiShowBadge = value }
    ),
    SettingDefinition.Switch(
        id = "ai_show_human_badge",
        titleRes = R.string.pref_ai_show_human_badge,
        subtitleRes = R.string.pref_ai_show_human_badge_sub,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.VerifiedUser,
        get = { it.aiShowHumanBadge },
        set = { prefs, value -> prefs.aiShowHumanBadge = value }
    ),
    SettingDefinition.Navigation(
        id = "blocked_tracks",
        titleRes = R.string.block_empty_tracks,
        subtitleRes = R.string.block_reason_manual,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.VisibilityOff
    ),
    SettingDefinition.Navigation(
        id = "blocked_artists",
        titleRes = R.string.block_empty_artists,
        subtitleRes = R.string.block_reason_manual,
        category = SettingsCategory.MISC,
        route = "content_filter_settings",
        icon = Icons.Rounded.PersonOff
    )
)
