@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.musicimport

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.vk.VkImportedPlaylist

/**
 * Importing a VK Music playlist from its link, start to finish, in one card.
 */
@Composable
fun VkImportCard(
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val state by VkImportSession.state.collectAsState()
    var link by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = VkBlue, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("VK", color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vk_import_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.vk_import_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val isBusy = state is VkImportState.Loading || state is VkImportState.Importing
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    enabled = !isBusy,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.vk_import_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { clipboard.getText()?.text?.let { link = it.trim() } }, enabled = !isBusy) {
                            Icon(Icons.Rounded.ContentPaste, stringResource(R.string.vk_import_paste))
                        }
                    },
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { VkImportSession.load(link) },
                    enabled = link.isNotBlank() && !isBusy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(52.dp),
                ) { Text(stringResource(R.string.vk_import_load)) }
            }

            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                label = "vkImport",
            ) { shown ->
                when (shown) {
                    VkImportState.Idle -> Text(stringResource(R.string.vk_import_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VkImportState.Loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                    is VkImportState.Problem -> ProblemLine(shown)
                    is VkImportState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlaylistHeader(shown.playlist, stringResource(R.string.vk_import_tracks, shown.playlist.tracks.size))
                        Button(onClick = { VkImportSession.start() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.vk_import_start))
                        }
                    }
                    is VkImportState.Importing -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val progress = shown.progress
                        PlaylistHeader(shown.playlist, stringResource(R.string.vk_import_progress, progress.done, progress.total, progress.found))
                        val fraction by animateFloatAsState(if (progress.total == 0) 0f else progress.done.toFloat() / progress.total, label = "vkImportProgress")
                        LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    }
                    is VkImportState.Done -> DoneSection(shown, onOpenPlaylist)
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: VkImportedPlaylist, line: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
            if (playlist.coverUrl != null) {
                AsyncImage(model = playlist.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
            } else {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(64.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null) }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (playlist.author.isNotBlank()) {
                Text(playlist.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(line, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProblemLine(problem: VkImportState.Problem) {
    val text = when (problem.reason) {
        VkImportState.Reason.NOT_VK -> stringResource(R.string.vk_import_not_vk)
        VkImportState.Reason.NOT_A_PLAYLIST -> stringResource(R.string.vk_import_not_playlist)
        VkImportState.Reason.NOT_AVAILABLE -> stringResource(R.string.vk_import_private)
        VkImportState.Reason.EMPTY -> stringResource(R.string.vk_import_empty)
        VkImportState.Reason.FAILED -> stringResource(R.string.vk_import_failed, problem.detail.orEmpty())
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DoneSection(done: VkImportState.Done, onOpenPlaylist: (Long) -> Unit) {
    var showMissing by remember { mutableStateOf(false) }
    val result = done.result
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.vk_import_done, result.imported, done.playlist.tracks.size, done.playlist.title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onOpenPlaylist(result.playlistId) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.vk_import_open)) }
            FilledTonalButton(onClick = { VkImportSession.reset() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.vk_import_another)) }
        }
        if (result.missing.isNotEmpty()) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showMissing = !showMissing }) {
                Text(stringResource(R.string.vk_import_missing, result.missing.size))
                Icon(if (showMissing) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(showMissing) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    result.missing.forEach { track ->
                        Text(
                            "${track.artist} — ${track.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The import card in a dialog, opened from the VK row of the platform list. */
@Composable
fun VkImportDialog(onDismiss: () -> Unit, onOpenPlaylist: (Long) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        VkImportCard(
            onOpenPlaylist = onOpenPlaylist,
            modifier = Modifier.widthIn(max = 560.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** VK's own blue, for the badge that says where the playlist comes from. */
private val VkBlue = Color(0xFF0077FF)
