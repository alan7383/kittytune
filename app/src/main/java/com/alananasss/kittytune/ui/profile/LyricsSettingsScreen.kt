package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsScreen(
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    val fontSize = playerViewModel.lyricsFontSize
    val alignment = playerViewModel.lyricsAlignment
    var preferLocal by remember { mutableStateOf(prefs.getLyricsPreferLocal()) }

    var showAlignmentDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }

    // Dialog Taille Police
    if (showFontSizeDialog) {
        Dialog(onDismissRequest = { showFontSizeDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.pref_lyrics_size),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${fontSize.roundToInt()} sp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(60.dp)
                        )

                        IconButton(
                            onClick = {
                                val newSize = (fontSize - 2f).coerceAtLeast(12f)
                                playerViewModel.updateLyricsFontSize(newSize)
                            },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Rounded.Remove, null) }

                        Slider(
                            value = fontSize,
                            onValueChange = {
                                playerViewModel.updateLyricsFontSize(it)
                            },
                            valueRange = 12f..48f,
                            steps = 17,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = {
                                val newSize = (fontSize + 2f).coerceAtMost(48f)
                                playerViewModel.updateLyricsFontSize(newSize)
                            },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Rounded.Add, null) }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                playerViewModel.updateLyricsFontSize(26f) // Valeur par défaut
                            }
                        ) { Text(stringResource(R.string.pref_lyrics_reset)) }

                        Row {
                            TextButton(onClick = { showFontSizeDialog = false }) { Text(stringResource(R.string.btn_close)) }
                        }
                    }
                }
            }
        }
    }

    // Dialog Alignement
    if (showAlignmentDialog) {
        AlertDialog(
            onDismissRequest = { showAlignmentDialog = false },
            title = { Text(stringResource(R.string.pref_lyrics_align)) },
            text = {
                Column {
                    AlignRadioButton(stringResource(R.string.align_left), LyricsAlignment.LEFT, alignment) {
                        playerViewModel.updateLyricsAlignment(it)
                        showAlignmentDialog = false
                    }
                    AlignRadioButton(stringResource(R.string.align_center), LyricsAlignment.CENTER, alignment) {
                        playerViewModel.updateLyricsAlignment(it)
                        showAlignmentDialog = false
                    }
                    AlignRadioButton(stringResource(R.string.align_right), LyricsAlignment.RIGHT, alignment) {
                        playerViewModel.updateLyricsAlignment(it)
                        showAlignmentDialog = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAlignmentDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_lyrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsCategoryHeader(stringResource(R.string.settings_cat_source))

                SwitchSettingItem(
                    icon = Icons.Rounded.SdStorage,
                    title = stringResource(R.string.pref_lyrics_local),
                    subtitle = stringResource(R.string.pref_lyrics_local_sub),
                    checked = preferLocal,
                    onCheckedChange = {
                        preferLocal = it
                        prefs.setLyricsPreferLocal(it)
                    }
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                SettingsCategoryHeader(stringResource(R.string.settings_cat_appearance))

                SettingsItemWithSubtitle(
                    icon = Icons.Rounded.FormatAlignLeft,
                    title = stringResource(R.string.pref_lyrics_align),
                    subtitle = when(alignment) {
                        LyricsAlignment.LEFT -> stringResource(R.string.align_left)
                        LyricsAlignment.CENTER -> stringResource(R.string.align_center_simple)
                        LyricsAlignment.RIGHT -> stringResource(R.string.align_right)
                    },
                    onClick = { showAlignmentDialog = true }
                )

                SettingsItemWithSubtitle(
                    icon = Icons.Rounded.FormatSize,
                    title = stringResource(R.string.pref_lyrics_size),
                    subtitle = "${fontSize.roundToInt()} sp",
                    onClick = { showFontSizeDialog = true }
                )
            }
        }
    }
}

@Composable
fun AlignRadioButton(text: String, mode: LyricsAlignment, selected: LyricsAlignment, onSelect: (LyricsAlignment) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (mode == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}