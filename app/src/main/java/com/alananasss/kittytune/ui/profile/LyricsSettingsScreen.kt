    package com.alananasss.kittytune.ui.profile
    
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.Article // Import ajouté
    import androidx.compose.material.icons.rounded.Description
    import androidx.compose.material.icons.rounded.FormatAlignLeft
    import androidx.compose.material.icons.rounded.FormatSize
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.SdStorage
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Shape
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.window.Dialog
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.local.LyricsAlignment
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.common.SettingsGroupTitle
    import kotlin.math.roundToInt
    
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
        var showLyricsButton by remember { mutableStateOf(prefs.getShowLyricsButtonEnabled()) }
        var inlineLyrics by remember { mutableStateOf(prefs.getInlineLyricsEnabled()) }
    
        var showAlignmentDialog by remember { mutableStateOf(false) }
        var showFontSizeDialog by remember { mutableStateOf(false) }
    
        // --- DIALOGS ---
    
        if (showFontSizeDialog) {
            Dialog(onDismissRequest = { showFontSizeDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.pref_lyrics_size), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${fontSize.roundToInt()} sp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsFontSize((fontSize - 2f).coerceAtLeast(12f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = fontSize,
                                onValueChange = { playerViewModel.updateLyricsFontSize(it) },
                                valueRange = 12f..48f,
                                steps = 17,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsFontSize((fontSize + 2f).coerceAtMost(48f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsFontSize(26f) }) { Text(stringResource(R.string.pref_lyrics_reset)) }
                            TextButton(onClick = { showFontSizeDialog = false }) { Text(stringResource(R.string.btn_close)) }
                        }
                    }
                }
            }
        }
    
        if (showAlignmentDialog) {
            AlertDialog(
                onDismissRequest = { showAlignmentDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_align)) },
                text = {
                    Column {
                        AlignRadioButton(stringResource(R.string.align_left), LyricsAlignment.LEFT, alignment) { playerViewModel.updateLyricsAlignment(it); showAlignmentDialog = false }
                        AlignRadioButton(stringResource(R.string.align_center), LyricsAlignment.CENTER, alignment) { playerViewModel.updateLyricsAlignment(it); showAlignmentDialog = false }
                        AlignRadioButton(stringResource(R.string.align_right), LyricsAlignment.RIGHT, alignment) { playerViewModel.updateLyricsAlignment(it); showAlignmentDialog = false }
                    }
                },
                confirmButton = { TextButton(onClick = { showAlignmentDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }
    
        // --- MAIN SCREEN ---
    
        SettingsScaffold(
            title = stringResource(R.string.pref_lyrics_title),
            onBackClick = onBackClick
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // SOURCE
                item {
                    SettingsGroup(
                        title = stringResource(R.string.settings_cat_source),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_lyrics_local),
                                    subtitle = stringResource(R.string.pref_lyrics_local_sub),
                                    icon = Icons.Rounded.SdStorage,
                                    hasSwitch = true,
                                    switchState = preferLocal,
                                    onSwitchChange = {
                                        preferLocal = it
                                        prefs.setLyricsPreferLocal(it)
                                    }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_lyrics_precise),
                                    subtitle = stringResource(R.string.pref_lyrics_precise_sub),
                                    icon = Icons.Rounded.Description,
                                    hasSwitch = true,
                                    switchState = playerViewModel.isPreciseLyricsSearchEnabled,
                                    onSwitchChange = {
                                        playerViewModel.togglePreciseLyricsSearch(it)
                                    }
                                )
                            }
                        )
                    )
                }
    
                // APPEARANCE REWORKED
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingsGroupTitle(stringResource(R.string.settings_cat_appearance))
    
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    
                            val totalVisibleItems = if (showLyricsButton) 4 else 3
    
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, 0),
                                title = stringResource(R.string.pref_lyrics_show_button),
                                subtitle = stringResource(R.string.pref_lyrics_show_button_sub),
                                icon = Icons.Rounded.Description,
                                hasSwitch = true,
                                switchState = showLyricsButton,
                                onSwitchChange = {
                                    showLyricsButton = it
                                    prefs.setShowLyricsButtonEnabled(it)
                                }
                            )
    
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showLyricsButton,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                SettingsItem(
                                    shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, 1),
                                    title = stringResource(R.string.pref_lyrics_inline),
                                    subtitle = stringResource(R.string.pref_lyrics_inline_sub),
                                    icon = Icons.Rounded.Article,
                                    hasSwitch = true,
                                    switchState = inlineLyrics,
                                    onSwitchChange = {
                                        inlineLyrics = it
                                        prefs.setInlineLyricsEnabled(it)
                                    }
                                )
                            }
    
                            val alignIndex = if (showLyricsButton) 2 else 1
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, alignIndex),
                                title = stringResource(R.string.pref_lyrics_align),
                                subtitle = when(alignment) {
                                    LyricsAlignment.LEFT -> stringResource(R.string.align_left)
                                    LyricsAlignment.CENTER -> stringResource(R.string.align_center_simple)
                                    LyricsAlignment.RIGHT -> stringResource(R.string.align_right)
                                },
                                icon = Icons.Rounded.FormatAlignLeft,
                                onClick = { showAlignmentDialog = true }
                            )
    
                            val sizeIndex = if (showLyricsButton) 3 else 2
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, sizeIndex),
                                title = stringResource(R.string.pref_lyrics_size),
                                subtitle = "${fontSize.roundToInt()} sp",
                                icon = Icons.Rounded.FormatSize,
                                onClick = { showFontSizeDialog = true }
                            )
                        }
                    }
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


