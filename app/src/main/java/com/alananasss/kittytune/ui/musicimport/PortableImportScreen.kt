package com.alananasss.kittytune.ui.musicimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.musicimport.PortableMatchConfidence
import com.alananasss.kittytune.data.musicimport.PortableResolvedEntry
import com.alananasss.kittytune.ui.common.SettingsScaffold

/**
 * Guest-friendly import: a TXT or CSV file, no SoundCloud account, no OAuth.
 *
 * The screen parses first, then resolves each row against the catalogue while
 * showing progress. Nothing is written to the library until the user presses
 * Import, so a bad file can be inspected (and rows can be unchecked) first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortableImportScreen(
    onBackClick: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: PortableImportViewModel = viewModel(),
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.load(it) }
    }

    val chooseFile = {
        filePicker.launch(
            arrayOf(
                "text/*",
                "text/csv",
                "text/plain",
                "text/comma-separated-values",
                "application/csv",
                "application/vnd.ms-excel",
                "application/octet-stream",
            )
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.portable_import_title),
        onBackClick = onBackClick,
        actions = {
            if (viewModel.entries.isNotEmpty()) {
                IconButton(
                    onClick = chooseFile,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.portable_import_choose_file),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                viewModel.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.portable_import_reading))
                    }
                }

                viewModel.entries.isEmpty() -> {
                    EmptyPortableImport(
                        error = viewModel.error,
                        onChooseFile = chooseFile,
                    )
                }

                else -> {
                    PortableImportContent(
                        viewModel = viewModel,
                        onChooseFile = chooseFile,
                    )
                }
            }
        }
    }

    if (viewModel.importFinished) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissFinished()
                onBackClick()
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.portable_import_done_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.portable_import_done_message,
                        viewModel.importedCount,
                        viewModel.skippedCount,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = hapticAction(HapticKind.UI) {
                        viewModel.dismissFinished()
                        viewModel.importedPlaylistId?.let(onOpenPlaylist) ?: onBackClick()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.portable_import_open_playlist))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = hapticAction(HapticKind.UI) {
                        viewModel.dismissFinished()
                        onBackClick()
                    }
                ) {
                    Text(stringResource(R.string.portable_import_done))
                }
            },
        )
    }
}

@Composable
private fun EmptyPortableImport(
    error: String?,
    onChooseFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(84.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.portable_import_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.portable_import_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.portable_import_formats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onChooseFile,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.portable_import_choose_file),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PortableImportContent(
    viewModel: PortableImportViewModel,
    onChooseFile: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = viewModel.playlistName,
                onValueChange = { viewModel.playlistName = it },
                label = { Text(stringResource(R.string.portable_import_playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.portable_import_summary,
                        viewModel.entries.size,
                        viewModel.matchedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (viewModel.isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            if (viewModel.isResolving) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.portable_import_add_to_likes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.portable_import_add_to_likes_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = viewModel.importToLikes,
                    onCheckedChange = { viewModel.updateImportToLikes(it) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = hapticAction(HapticKind.UI) { viewModel.selectAll() }) {
                    Text(stringResource(R.string.portable_import_select_all))
                }
                TextButton(onClick = hapticAction(HapticKind.UI) { viewModel.selectNone() }) {
                    Text(stringResource(R.string.portable_import_select_none))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onChooseFile) {
                    Text(stringResource(R.string.portable_import_choose_file))
                }
            }
            viewModel.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            itemsIndexed(
                items = viewModel.entries,
                key = { index, entry ->
                    "$index-${entry.entry.title}-${entry.entry.url.orEmpty()}"
                },
            ) { index, entry ->
                PortableEntryRow(
                    entry = entry,
                    selected = index in viewModel.selectedIndices,
                    enabled = entry.isMatched,
                    onToggle = { viewModel.toggleSelected(index) },
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = hapticAction(HapticKind.UI) { viewModel.importSelected {} },
                enabled = !viewModel.isImporting && !viewModel.isResolving && viewModel.selectedTrackCount > 0,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(52.dp),
            ) {
                if (viewModel.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.portable_import_start,
                            viewModel.selectedTrackCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortableEntryRow(
    entry: PortableResolvedEntry,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.tracks.firstOrNull()?.title
                    ?: entry.entry.title.ifBlank { entry.entry.url.orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    val artist = entry.tracks.firstOrNull()?.displayArtist
                        ?.takeIf { it.isNotBlank() }
                        ?: entry.entry.artist
                    if (artist.isNotBlank()) append(artist)
                    if (entry.tracks.size > 1) {
                        if (isNotEmpty()) append(" • ")
                        append("${entry.tracks.size} tracks")
                    }
                }.ifBlank { entry.entry.url.orEmpty() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            !entry.isMatched -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.portable_import_not_found),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            entry.confidence == PortableMatchConfidence.LIKELY -> {
                Text(
                    text = stringResource(R.string.portable_import_likely),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = stringResource(R.string.portable_import_matched),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
