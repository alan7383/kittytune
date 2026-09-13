    package com.alananasss.kittytune.ui.profile

    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.Article
    import androidx.compose.material.icons.rounded.Description
    import androidx.compose.material.icons.rounded.FormatAlignLeft
    import androidx.compose.material.icons.rounded.FormatSize
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.SdStorage
    import androidx.compose.material3.*
    import com.alananasss.kittytune.ui.common.Slider
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
    import com.alananasss.kittytune.data.local.LyricsUnderCoverPlacement
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import com.alananasss.kittytune.ui.common.SettingsGroupTitle
    import com.alananasss.kittytune.ui.common.getSettingsShape
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import androidx.compose.material3.OutlinedTextField
    import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
    import com.alananasss.kittytune.data.lyrics.providers.DefaultLyricsProviderOrder
    import com.alananasss.kittytune.data.lyrics.clients.PaxsenixClient
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

    var provider by remember { mutableStateOf(playerViewModel.lyricsProvider) }
    var enableTranslation by remember { mutableStateOf(playerViewModel.isLyricsTranslationEnabled) }
    var targetLang by remember { mutableStateOf(playerViewModel.lyricsTranslationLang) }

    var showProviderDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }

    var lyricsUnderCover by remember { mutableStateOf(prefs.getLyricsUnderCoverEnabled()) }
    var lyricsMultiState by remember { mutableStateOf(prefs.getLyricsMultiStateToggle()) }
    var lyricsUnderCoverPlacement by remember { mutableStateOf(prefs.getLyricsUnderCoverPlacement()) }
    var lyricsUnderCoverAlways by remember { mutableStateOf(prefs.getLyricsUnderCoverAlwaysVisible()) }
    var showPlacementDialog by remember { mutableStateOf(false) }
    var showPaxsenixKeyDialog by remember { mutableStateOf(false) }
    var paxsenixKeyInput by remember { mutableStateOf(prefs.getPaxsenixApiKey()) }
    var showProviderOrderDialog by remember { mutableStateOf(false) }
    var providerOrder by remember { mutableStateOf(prefs.getLyricsProviderOrder()) }

    var showUiStyleDialog by remember { mutableStateOf(false) }
    var showLyricsFontDialog by remember { mutableStateOf(false) }
    var showBounceFactorDialog by remember { mutableStateOf(false) }
    var showGlowFactorDialog by remember { mutableStateOf(false) }
    var showFillTransitionDialog by remember { mutableStateOf(false) }
    var showLineSpacingDialog by remember { mutableStateOf(false) }

        if (showProviderDialog) {
            AlertDialog(
                onDismissRequest = { showProviderDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_provider_title)) },
                text = {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable {
                            provider = com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY
                            playerViewModel.updateLyricsProvider(provider)
                            showProviderDialog = false
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = (provider == com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pref_lyrics_provider_max_quality))
                        }
                        Row(Modifier.fillMaxWidth().clickable {
                            provider = com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE
                            playerViewModel.updateLyricsProvider(provider)
                            showProviderDialog = false
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = (provider == com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pref_lyrics_provider_open_source))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showProviderDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }

        if (showLangDialog) {
            val systemLangCode = java.util.Locale.getDefault().language
            val systemLabel = stringResource(R.string.theme_system)
            val allLanguages = remember {
                val locales = java.util.Locale.getISOLanguages()
                    .map { code ->
                        val loc = java.util.Locale.forLanguageTag(code)
                        code to loc.getDisplayLanguage(loc).replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
                    }
                    .filter { it.second.isNotBlank() && it.first.length == 2 }
                    .distinctBy { it.first }
                    .sortedBy { it.second }

                val list = mutableListOf<Pair<String, String>>()
                val systemLoc = locales.find { it.first == systemLangCode }
                if (systemLoc != null) {
                    list.add(systemLoc.first to "${systemLoc.second} ($systemLabel)")
                }
                list.addAll(locales.filter { it.first != systemLangCode })
                list
            }

            AlertDialog(
                onDismissRequest = { showLangDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_translation_lang)) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(allLanguages) { (code, name) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    targetLang = code
                                    showLangDialog = false
                                    playerViewModel.updateLyricsTranslationLang(code)
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (targetLang == code), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showLangDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }

        if (showPlacementDialog) {
            AlertDialog(
                onDismissRequest = { showPlacementDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_under_cover_placement)) },
                text = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                lyricsUnderCoverPlacement = LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST
                                prefs.setLyricsUnderCoverPlacement(lyricsUnderCoverPlacement)
                                showPlacementDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (lyricsUnderCoverPlacement == LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pref_lyrics_under_cover_replace))
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                lyricsUnderCoverPlacement = LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST
                                prefs.setLyricsUnderCoverPlacement(lyricsUnderCoverPlacement)
                                showPlacementDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (lyricsUnderCoverPlacement == LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pref_lyrics_under_cover_above))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlacementDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        if (showPaxsenixKeyDialog) {
            var tempKey by remember { mutableStateOf(paxsenixKeyInput) }
            AlertDialog(
                onDismissRequest = { showPaxsenixKeyDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_paxsenix_key)) },
                text = {
                    Column {
                        Text(stringResource(R.string.pref_lyrics_paxsenix_key_sub), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Bearer token...") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        paxsenixKeyInput = tempKey
                        prefs.setPaxsenixApiKey(tempKey)
                        PaxsenixClient.setApiKey(tempKey)
                        showPaxsenixKeyDialog = false
                    }) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPaxsenixKeyDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        if (showProviderOrderDialog) {
            var currentOrder by remember { mutableStateOf(prefs.getLyricsProviderOrder().toMutableList()) }
            AlertDialog(
                onDismissRequest = { showProviderOrderDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_order)) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(currentOrder.size) { index ->
                            val p = currentOrder[index]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${index + 1}. ${p.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    if (index > 0) {
                                        IconButton(onClick = {
                                            val list = currentOrder.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index - 1, item)
                                            currentOrder = list
                                        }) {
                                            Text("▲")
                                        }
                                    }
                                    if (index < currentOrder.size - 1) {
                                        IconButton(onClick = {
                                            val list = currentOrder.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index + 1, item)
                                            currentOrder = list
                                        }) {
                                            Text("▼")
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        providerOrder = currentOrder
                        prefs.setLyricsProviderOrder(currentOrder)
                        showProviderOrderDialog = false
                    }) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showProviderOrderDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        if (showUiStyleDialog) {
            AlertDialog(
                onDismissRequest = { showUiStyleDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_ui_style_title)) },
                text = {
                    Column {
                        com.alananasss.kittytune.data.local.LyricsUiStyle.entries.forEach { style ->
                            val label = when (style) {
                                com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED -> stringResource(R.string.pref_lyrics_ui_style_enhanced)
                                com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> stringResource(R.string.pref_lyrics_ui_style_classic)
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    playerViewModel.updateLyricsUiStyle(style)
                                    showUiStyleDialog = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (playerViewModel.lyricsUiStyle == style), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showUiStyleDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        if (showLyricsFontDialog) {
            AlertDialog(
                onDismissRequest = { showLyricsFontDialog = false },
                title = { Text(stringResource(R.string.pref_lyrics_font_title)) },
                text = {
                    Column {
                        com.alananasss.kittytune.data.local.LyricsFont.entries.forEach { font ->
                            val label = when (font) {
                                com.alananasss.kittytune.data.local.LyricsFont.APPLE -> stringResource(R.string.pref_lyrics_font_apple)
                                com.alananasss.kittytune.data.local.LyricsFont.APP_DEFAULT -> stringResource(R.string.pref_lyrics_font_app_default)
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    playerViewModel.updateLyricsFont(font)
                                    showLyricsFontDialog = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (playerViewModel.lyricsFont == font), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLyricsFontDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        if (showBounceFactorDialog) {
            Dialog(onDismissRequest = { showBounceFactorDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.pref_lyrics_bounce_factor_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${(playerViewModel.lyricsBounceFactor * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsBounceFactor((playerViewModel.lyricsBounceFactor - 0.1f).coerceAtLeast(0f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsBounceFactor,
                                onValueChange = { playerViewModel.updateLyricsBounceFactor(it) },
                                valueRange = 0f..2f,
                                steps = 19,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsBounceFactor((playerViewModel.lyricsBounceFactor + 0.1f).coerceAtMost(2f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsBounceFactor(1f) }) { Text(stringResource(R.string.pref_lyrics_reset)) }
                            TextButton(onClick = { showBounceFactorDialog = false }) { Text(stringResource(R.string.btn_close)) }
                        }
                    }
                }
            }
        }

        if (showGlowFactorDialog) {
            Dialog(onDismissRequest = { showGlowFactorDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.pref_lyrics_glow_factor_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${(playerViewModel.lyricsGlowFactor * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsGlowFactor((playerViewModel.lyricsGlowFactor - 0.1f).coerceAtLeast(0f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsGlowFactor,
                                onValueChange = { playerViewModel.updateLyricsGlowFactor(it) },
                                valueRange = 0f..2f,
                                steps = 19,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsGlowFactor((playerViewModel.lyricsGlowFactor + 0.1f).coerceAtMost(2f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsGlowFactor(1f) }) { Text(stringResource(R.string.pref_lyrics_reset)) }
                            TextButton(onClick = { showGlowFactorDialog = false }) { Text(stringResource(R.string.btn_close)) }
                        }
                    }
                }
            }
        }

        if (showFillTransitionDialog) {
            Dialog(onDismissRequest = { showFillTransitionDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.pref_lyrics_fill_transition_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${playerViewModel.lyricsFillTransitionWidth.toInt()} dp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsFillTransitionWidth((playerViewModel.lyricsFillTransitionWidth - 2f).coerceAtLeast(2f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsFillTransitionWidth,
                                onValueChange = { playerViewModel.updateLyricsFillTransitionWidth(it) },
                                valueRange = 2f..24f,
                                steps = 10,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsFillTransitionWidth((playerViewModel.lyricsFillTransitionWidth + 2f).coerceAtMost(24f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsFillTransitionWidth(8f) }) { Text(stringResource(R.string.pref_lyrics_reset)) }
                            TextButton(onClick = { showFillTransitionDialog = false }) { Text(stringResource(R.string.btn_close)) }
                        }
                    }
                }
            }
        }

        if (showLineSpacingDialog) {
            Dialog(onDismissRequest = { showLineSpacingDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.pref_lyrics_line_spacing_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${playerViewModel.lyricsLineSpacing.toInt()} dp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsLineSpacing((playerViewModel.lyricsLineSpacing - 2f).coerceAtLeast(12f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsLineSpacing,
                                onValueChange = { playerViewModel.updateLyricsLineSpacing(it) },
                                valueRange = 12f..48f,
                                steps = 17,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsLineSpacing((playerViewModel.lyricsLineSpacing + 2f).coerceAtMost(48f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsLineSpacing(24f) }) { Text(stringResource(R.string.pref_lyrics_reset)) }
                            TextButton(onClick = { showLineSpacingDialog = false }) { Text(stringResource(R.string.btn_close)) }
                        }
                    }
                }
            }
        }

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

        SettingsScaffold(
            title = stringResource(R.string.pref_lyrics_title),
            onBackClick = onBackClick
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 180.dp)
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
                                    title = stringResource(R.string.pref_lyrics_word_sync),
                                    subtitle = stringResource(R.string.pref_lyrics_word_sync_sub),
                                    hasSwitch = true,
                                    switchState = playerViewModel.isWordSyncEnabled,
                                    onSwitchChange = { playerViewModel.toggleWordSync(it) }
                                )
                            },
                            { shape ->
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = playerViewModel.isWordSyncEnabled,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    SettingsItem(
                                        shape = shape,
                                        title = stringResource(R.string.pref_lyrics_apple_effect),
                                        subtitle = stringResource(R.string.pref_lyrics_apple_effect_sub),
                                        hasSwitch = true,
                                        switchState = playerViewModel.isAppleMusicEffectEnabled,
                                        onSwitchChange = { playerViewModel.toggleAppleMusicEffect(it) }
                                    )
                                }
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_lyrics_duet_title),
                                    subtitle = stringResource(R.string.pref_lyrics_duet_desc),
                                    hasSwitch = true,
                                    switchState = playerViewModel.isDuetViewEnabled,
                                    onSwitchChange = { playerViewModel.toggleDuetView(it) }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_lyrics_romanization),
                                    subtitle = stringResource(R.string.pref_lyrics_romanization_sub),
                                    hasSwitch = true,
                                    switchState = playerViewModel.isRomanizationEnabled,
                                    onSwitchChange = { playerViewModel.toggleRomanization(it) }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_lyrics_translation_title),
                                    subtitle = stringResource(R.string.pref_lyrics_translation_sub),
                                    hasSwitch = true,
                                    switchState = playerViewModel.isLyricsTranslationEnabled,
                                    onSwitchChange = {
                                        enableTranslation = it
                                        playerViewModel.toggleLyricsTranslation(it)
                                    }
                                )
                            },
                            { shape ->
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = enableTranslation,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    SettingsItem(
                                        shape = shape,
                                        title = stringResource(R.string.pref_lyrics_translation_lang),
                                        subtitle = targetLang.uppercase(),
                                        onClick = { showLangDialog = true }
                                    )
                                }
                            },
                            { shape ->
                                var androidAutoLyrics by remember { mutableStateOf(prefs.getAndroidAutoSyncedLyricsEnabled()) }
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_android_auto_synced_lyrics),
                                    subtitle = stringResource(R.string.pref_android_auto_synced_lyrics_desc),
                                    hasSwitch = true,
                                    switchState = androidAutoLyrics,
                                    onSwitchChange = {
                                        androidAutoLyrics = it
                                        prefs.setAndroidAutoSyncedLyricsEnabled(it)
                                    }
                                )
                            }
                        )
                    )
                }

                item {
                    val providers = PreferredLyricsProvider.entries
                    val itemsList = mutableListOf<@Composable (Shape) -> Unit>()
                    itemsList.add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_lyrics_order),
                            subtitle = stringResource(R.string.pref_lyrics_order_sub),
                            onClick = { showProviderOrderDialog = true }
                        )
                    }
                    itemsList.add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_lyrics_paxsenix_key),
                            subtitle = if (paxsenixKeyInput.isNotBlank()) "••••••••" else stringResource(R.string.pref_lyrics_paxsenix_key_sub),
                            onClick = { showPaxsenixKeyDialog = true }
                        )
                    }
                    providers.forEach { p ->
                        itemsList.add { shape ->
                            var enabled by remember { mutableStateOf(prefs.getLyricsProviderEnabled(p)) }
                            SettingsItem(
                                shape = shape,
                                title = p.displayName,
                                subtitle = stringResource(R.string.pref_lyrics_enable_provider, p.displayName),
                                hasSwitch = true,
                                switchState = enabled,
                                onSwitchChange = {
                                    enabled = it
                                    prefs.setLyricsProviderEnabled(p, it)
                                }
                            )
                        }
                    }

                    SettingsGroup(
                        title = stringResource(R.string.pref_lyrics_providers_category),
                        items = itemsList
                    )
                }

                // LYRICS UI STYLE & ANIMATIONS
                item {
                    val isEnhanced = playerViewModel.lyricsUiStyle == com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED
                    val styleItems = mutableListOf<@Composable (Shape) -> Unit>()
                    styleItems.add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_lyrics_ui_style_title),
                            subtitle = when (playerViewModel.lyricsUiStyle) {
                                com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED -> stringResource(R.string.pref_lyrics_ui_style_enhanced)
                                com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> stringResource(R.string.pref_lyrics_ui_style_classic)
                            },
                            onClick = { showUiStyleDialog = true }
                        )
                    }
                    styleItems.add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_lyrics_font_title),
                            subtitle = when (playerViewModel.lyricsFont) {
                                com.alananasss.kittytune.data.local.LyricsFont.APPLE -> stringResource(R.string.pref_lyrics_font_apple)
                                com.alananasss.kittytune.data.local.LyricsFont.APP_DEFAULT -> stringResource(R.string.pref_lyrics_font_app_default)
                            },
                            onClick = { showLyricsFontDialog = true }
                        )
                    }
                    if (isEnhanced) {
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_line_blur_title),
                                subtitle = stringResource(R.string.pref_lyrics_line_blur_desc),
                                hasSwitch = true,
                                switchState = playerViewModel.lyricsLineBlurEnabled,
                                onSwitchChange = { playerViewModel.updateLyricsLineBlurEnabled(it) }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_lrc_bounce_title),
                                subtitle = stringResource(R.string.pref_lyrics_lrc_bounce_desc),
                                hasSwitch = true,
                                switchState = playerViewModel.lyricsLrcBounceEnabled,
                                onSwitchChange = { playerViewModel.updateLyricsLrcBounceEnabled(it) }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_bounce_factor_title),
                                subtitle = "${(playerViewModel.lyricsBounceFactor * 100).toInt()}%",
                                onClick = { showBounceFactorDialog = true }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_glow_factor_title),
                                subtitle = "${(playerViewModel.lyricsGlowFactor * 100).toInt()}%",
                                onClick = { showGlowFactorDialog = true }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_fill_transition_title),
                                subtitle = "${playerViewModel.lyricsFillTransitionWidth.toInt()} dp",
                                onClick = { showFillTransitionDialog = true }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_line_spacing_title),
                                subtitle = "${playerViewModel.lyricsLineSpacing.toInt()} dp",
                                onClick = { showLineSpacingDialog = true }
                            )
                        }
                    }

                    SettingsGroup(
                        title = stringResource(R.string.pref_lyrics_effects_category),
                        items = styleItems
                    )
                }

                // APPEARANCE REWORKED
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingsGroupTitle(stringResource(R.string.settings_cat_appearance))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

                            val inlineIndex = 2
                            val underCoverIndex = if (showLyricsButton) 3 else 2
                            val multiStateIndex = underCoverIndex + 1
                            val placementIndex = underCoverIndex + 2
                            val alwaysIndex = underCoverIndex + 3
                            val alignIndex = underCoverIndex + (if (lyricsUnderCover) 4 else 1)
                            val sizeIndex = alignIndex + 1
                            val totalVisibleItems = sizeIndex + 1

                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 0),
                                title = stringResource(R.string.pref_lyrics_provider_title),
                                subtitle = if (provider == com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY) stringResource(R.string.pref_lyrics_provider_max_quality) else stringResource(R.string.pref_lyrics_provider_open_source),
                                onClick = { showProviderDialog = true }
                            )

                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 1),
                                title = stringResource(R.string.pref_lyrics_show_button),
                                subtitle = stringResource(R.string.pref_lyrics_show_button_sub),
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
                                    shape = getSettingsShape(totalVisibleItems, inlineIndex),
                                    title = stringResource(R.string.pref_lyrics_inline),
                                    subtitle = stringResource(R.string.pref_lyrics_inline_sub),
                                    hasSwitch = true,
                                    switchState = inlineLyrics,
                                    onSwitchChange = {
                                        inlineLyrics = it
                                        prefs.setInlineLyricsEnabled(it)
                                    }
                                )
                            }

                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, underCoverIndex),
                                title = stringResource(R.string.pref_lyrics_under_cover),
                                subtitle = stringResource(R.string.pref_lyrics_under_cover_sub),
                                hasSwitch = true,
                                switchState = lyricsUnderCover,
                                onSwitchChange = {
                                    lyricsUnderCover = it
                                    prefs.setLyricsUnderCoverEnabled(it)
                                }
                            )

                            androidx.compose.animation.AnimatedVisibility(
                                visible = lyricsUnderCover,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    SettingsItem(
                                        shape = getSettingsShape(totalVisibleItems, multiStateIndex),
                                        title = stringResource(R.string.pref_lyrics_multi_state),
                                        subtitle = stringResource(R.string.pref_lyrics_multi_state_sub),
                                        hasSwitch = true,
                                        switchState = lyricsMultiState,
                                        onSwitchChange = {
                                            lyricsMultiState = it
                                            prefs.setLyricsMultiStateToggle(it)
                                        }
                                    )

                                    SettingsItem(
                                        shape = getSettingsShape(totalVisibleItems, placementIndex),
                                        title = stringResource(R.string.pref_lyrics_under_cover_placement),
                                        subtitle = when (lyricsUnderCoverPlacement) {
                                            LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST -> stringResource(R.string.pref_lyrics_under_cover_replace)
                                            LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST -> stringResource(R.string.pref_lyrics_under_cover_above)
                                        },
                                        onClick = { showPlacementDialog = true }
                                    )

                                    SettingsItem(
                                        shape = getSettingsShape(totalVisibleItems, alwaysIndex),
                                        title = stringResource(R.string.pref_lyrics_under_cover_always),
                                        subtitle = stringResource(R.string.pref_lyrics_under_cover_always_sub),
                                        hasSwitch = true,
                                        switchState = lyricsUnderCoverAlways,
                                        onSwitchChange = {
                                            lyricsUnderCoverAlways = it
                                            prefs.setLyricsUnderCoverAlwaysVisible(it)
                                        }
                                    )
                                }
                            }

                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, alignIndex),
                                title = stringResource(R.string.pref_lyrics_align),
                                subtitle = when(alignment) {
                                    LyricsAlignment.LEFT -> stringResource(R.string.align_left)
                                    LyricsAlignment.CENTER -> stringResource(R.string.align_center_simple)
                                    LyricsAlignment.RIGHT -> stringResource(R.string.align_right)
                                },
                                onClick = { showAlignmentDialog = true }
                            )

                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, sizeIndex),
                                title = stringResource(R.string.pref_lyrics_size),
                                subtitle = "${fontSize.roundToInt()} sp",
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

