package com.alananasss.kittytune.ui.player.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.R
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.FullPlayerBgStyle
import com.alananasss.kittytune.data.local.FullPlayerHeartSide
import com.alananasss.kittytune.data.local.FullPlayerInfoAlign
import com.alananasss.kittytune.data.local.FullPlayerLayout
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.LyricsDisplayStyle
import com.alananasss.kittytune.data.local.LyricsFont
import com.alananasss.kittytune.data.local.LyricsUiStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.FunFactDot
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.LyricsProviderOrderDialog
import kotlin.math.roundToInt

private val PANEL_MAX_WIDTH = 680.dp
private val PANEL_MAX_HEIGHT = 900.dp

private enum class QuickTab(val labelKey: String, val icon: ImageVector) {
    LOOK("lyrics_quick_tab_look", Icons.Rounded.Palette),
    LAYOUT("lyrics_quick_tab_layout", Icons.Rounded.FormatSize),
    FEATURES("lyrics_quick_tab_features", Icons.Rounded.Tune),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLyricsSettingsDialog(
    viewModel: PlayerViewModel,
    isFullScreen: Boolean = false,
    isSidebar: Boolean = false,
    onFocusMode: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val knobs = remember(viewModel, isFullScreen, isSidebar) { ModeKnobs(viewModel, isFullScreen, isSidebar) }
    var tab by rememberSaveable { mutableStateOf(QuickTab.LOOK) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showSourceOrder by remember { mutableStateOf(false) }

    if (showLanguagePicker) {
        TranslationLanguageDialog(viewModel, onDismiss = { showLanguagePicker = false })
    }
    if (showSourceOrder) {
        LyricsProviderOrderDialog(
            prefs = viewModel.playerPrefs,
            onDismiss = { showSourceOrder = false },
        )
    }

    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(min(PANEL_MAX_WIDTH, maxWidth * 0.94f))
                    .heightIn(max = min(PANEL_MAX_HEIGHT, maxHeight * 0.94f)),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)) {
                    Header(
                        onSearch = {
                            onDismiss()
                            viewModel.isSearchingLyrics = true
                        },
                        onFocusMode = onFocusMode?.let { start -> { onDismiss(); start() } },
                        onDismiss = onDismiss,
                    )
                    Spacer(Modifier.height(16.dp))
                    val scroll = rememberScrollState()
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scroll)
                    ) {
                        SyncCard(viewModel)
                        Spacer(Modifier.height(14.dp))
                        ExpressiveConnectedButtonGroup(
                            options = QuickTab.entries,
                            selectedOption = tab,
                            onOptionSelected = { tab = it },
                            iconProvider = { Icon(it.icon, null, modifier = Modifier.size(18.dp)) },
                            labelProvider = {
                                Text(str(it.labelKey), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                            label = "quickLyricsTab",
                        ) { shown ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                when (shown) {
                                    QuickTab.LOOK -> LookTab(viewModel, knobs, isFullScreen)
                                    QuickTab.LAYOUT -> LayoutTab(viewModel, knobs)
                                    QuickTab.FEATURES -> FeaturesTab(
                                        viewModel,
                                        isFullScreen,
                                        onPickLanguage = { showLanguagePicker = true },
                                        onOpenSources = { showSourceOrder = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onSearch: () -> Unit, onFocusMode: (() -> Unit)?, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowIcon(Icons.Rounded.Lyrics, large = true)
        Spacer(Modifier.width(14.dp))
        Text(
            str("pref_lyrics_title"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Tip(str("lyrics_manual_search")) {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onSearch) {
                Icon(Icons.Rounded.Search, str("lyrics_manual_search"))
            }
        }
        if (onFocusMode != null) {
            Tip(str("screensaver_focus_mode")) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onFocusMode) {
                    Icon(Icons.Rounded.DarkMode, str("screensaver_focus_mode"))
                }
            }
        }
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDismiss) {
            Icon(Icons.Rounded.Close, str("btn_close"))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncCard(viewModel: PlayerViewModel) {
    val offsetMs = viewModel.lyricsOffset
    QuickCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.Timer)
            Spacer(Modifier.width(14.dp))
            Text(str("lyrics_sync"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ValuePill(
                text = (if (offsetMs > 0) "+" else "") + String.format("%.2fs", offsetMs / 1000f),
                isActive = offsetMs != 0L,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val steps = listOf(-1000L to "−1s", -100L to "−0.1s", 0L to "0", 100L to "+0.1s", 1000L to "+1s")
            steps.forEachIndexed { index, (delta, label) ->
                ToggleButton(
                    checked = delta == 0L && offsetMs == 0L,
                    onCheckedChange = { if (delta == 0L) viewModel.resetLyricsOffset() else viewModel.adjustLyricsOffset(delta) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        steps.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DriftSync(viewModel)
    }
}

@Composable
private fun DriftSync(viewModel: PlayerViewModel) {
    val sync = viewModel.lyricsSync
    val format = { ms: Long -> (if (ms > 0) "+" else "") + String.format("%.1f s", ms / 1000f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            str("lyrics_sync_drift_title"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Tip(str("lyrics_sync_drift_hint")) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = str("lyrics_sync_drift_hint"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val startSet = sync.isTwoPoint || sync.anchorMs > 0L
        OutlinedButton(
            onClick = { viewModel.pinLyricsSyncStart() },
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (startSet) str("lyrics_sync_point_start", com.alananasss.kittytune.utils.makeTimeString(sync.anchorMs))
                else str("lyrics_sync_pin_start"),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        val endAt = sync.endAtMs
        val tooClose = str("lyrics_sync_end_too_close")
        if (endAt != null) {
            Button(
                onClick = { if (!viewModel.pinLyricsSyncEnd()) Toaster.show(tooClose) },
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    str("lyrics_sync_point_end", com.alananasss.kittytune.utils.makeTimeString(endAt)),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        } else {
            OutlinedButton(
                onClick = { if (!viewModel.pinLyricsSyncEnd()) Toaster.show(tooClose) },
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(str("lyrics_sync_pin_end"), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        AnimatedVisibility(sync.isTwoPoint) {
            Tip(str("lyrics_sync_single")) {
                IconButton(onClick = { viewModel.clearLyricsSyncEnd() }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, str("lyrics_sync_single"))
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    if (sync.isTwoPoint) {
        Text(
            text = str(
                "lyrics_sync_drift_active",
                format(sync.offsetMs),
                com.alananasss.kittytune.utils.makeTimeString(sync.anchorMs),
                format(sync.endOffsetMs ?: 0L),
                com.alananasss.kittytune.utils.makeTimeString(sync.endAtMs ?: 0L),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Tabs ────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.LookTab(viewModel: PlayerViewModel, knobs: ModeKnobs, isFullScreen: Boolean) {
    ChoiceRow(
        icon = Icons.Rounded.Wallpaper,
        title = str("full_player_bg_style"),
        aside = if (viewModel.fullPlayerBgStyle == FullPlayerBgStyle.APPLE_MUSIC) {
            { FunFactDot(str("full_player_bg_apple_music_fact")) }
        } else null,
    ) {
        SketchChoices(
            options = FullPlayerBgStyle.entries,
            selected = viewModel.fullPlayerBgStyle,
            label = { bgStyleLabel(it) },
            onSelect = { viewModel.updateFullPlayerBgStyle(it) },
            sketch = { BackgroundSketch(it) },
        )
    }
    ChoiceRow(icon = Icons.Rounded.Dashboard, title = str("full_player_layout")) {
        SketchChoices(
            options = FullPlayerLayout.entries,
            selected = viewModel.fullPlayerLayout,
            label = { layoutLabel(it) },
            onSelect = { viewModel.updateFullPlayerLayout(it) },
            sketch = { LayoutSketch(it) },
        )
    }
    ChoiceRow(icon = Icons.Rounded.FormatAlignCenter, title = str("full_player_info_align")) {
        ExpressiveConnectedButtonGroup(
            options = FullPlayerInfoAlign.entries,
            selectedOption = viewModel.fullPlayerInfoAlign,
            onOptionSelected = { viewModel.updateFullPlayerInfoAlign(it) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            labelProvider = { ChoiceLabel(infoAlignLabel(it)) },
        )
    }
    AnimatedVisibility(viewModel.fullPlayerInfoAlign == FullPlayerInfoAlign.CENTER) {
        ChoiceRow(icon = Icons.Rounded.Favorite, title = str("full_player_heart_side")) {
            ExpressiveConnectedButtonGroup(
                options = FullPlayerHeartSide.entries,
                selectedOption = viewModel.fullPlayerHeartSide,
                onOptionSelected = { viewModel.updateFullPlayerHeartSide(it) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                labelProvider = { ChoiceLabel(heartSideLabel(it)) },
            )
        }
    }
    SwitchRow(
        icon = Icons.Rounded.BlurOn,
        title = str("full_player_cover_feather"),
        subtitle = str("full_player_cover_feather_desc"),
        checked = viewModel.fullPlayerCoverFeather,
        onChange = { viewModel.updateFullPlayerCoverFeather(it) },
    )
    AnimatedVisibility(viewModel.fullPlayerCoverFeather) {
        SliderRow(
            icon = Icons.Rounded.BlurLinear,
            title = str("full_player_cover_feather_amount"),
            value = viewModel.fullPlayerCoverFeatherAmount,
            valueText = "${(viewModel.fullPlayerCoverFeatherAmount * 100).roundToInt()}%",
            range = 0f..1f,
            steps = 19,
            onChange = { viewModel.updateFullPlayerCoverFeatherAmount(it) },
            onReset = { viewModel.updateFullPlayerCoverFeatherAmount(0.6f) },
        )
    }
    SliderRow(
        icon = Icons.Rounded.ZoomIn,
        title = str("full_player_cover_zoom"),
        value = viewModel.fullPlayerCoverScale,
        valueText = "${(viewModel.fullPlayerCoverScale * 100).roundToInt()}%",
        range = 0.6f..1.4f,
        steps = 15,
        onChange = { viewModel.updateFullPlayerCoverScale(it) },
        onReset = { viewModel.updateFullPlayerCoverScale(1f) },
    )
    ChoiceRow(icon = Icons.Rounded.AutoAwesome, title = str("pref_lyrics_ui_style_title")) {
        ExpressiveConnectedButtonGroup(
            options = LyricsUiStyle.entries,
            selectedOption = knobs.uiStyle,
            onOptionSelected = { knobs.uiStyle = it },
            labelProvider = {
                ChoiceLabel(if (it == LyricsUiStyle.ENHANCED) str("pref_lyrics_ui_style_enhanced") else str("pref_lyrics_ui_style_classic"))
            },
        )
    }
    AnimatedVisibility(knobs.uiStyle == LyricsUiStyle.CLASSIC) {
        ChoiceRow(icon = Icons.Rounded.Subtitles, title = str("pref_lyrics_display_style")) {
            LyricsDisplayStylePicker(selected = knobs.displayStyle, onSelect = { knobs.displayStyle = it })
        }
    }
    ChoiceRow(icon = Icons.Rounded.TextFields, title = str("pref_lyrics_font_title")) {
        val fonts = listOf(LyricsFont.APPLE, LyricsFont.APP_DEFAULT)
        ExpressiveConnectedButtonGroup(
            options = fonts,
            selectedOption = viewModel.lyricsFont,
            onOptionSelected = { viewModel.updateLyricsFont(it) },
            labelProvider = {
                ChoiceLabel(if (it == LyricsFont.APPLE) str("pref_lyrics_font_apple_short") else str("pref_lyrics_font_app_default_short"))
            },
        )
    }
    AnimatedVisibility(knobs.uiStyle != LyricsUiStyle.CLASSIC) {
        SwitchRow(
            icon = Icons.Rounded.BlurOn,
            title = str("pref_lyrics_line_blur_title"),
            subtitle = str("pref_lyrics_line_blur_desc"),
            checked = knobs.lineBlur,
            onChange = { knobs.lineBlur = it },
        )
    }
}

@Composable
private fun ColumnScope.LayoutTab(viewModel: PlayerViewModel, knobs: ModeKnobs) {
    SliderRow(
        icon = Icons.Rounded.FormatSize,
        title = str("pref_lyrics_size"),
        value = knobs.fontSize,
        valueText = "${knobs.fontSize.roundToInt()} sp",
        range = 12f..100f,
        steps = 43,
        onChange = { knobs.fontSize = it },
        onReset = { knobs.fontSize = knobs.defaultFontSize },
    )
    ChoiceRow(icon = Icons.Rounded.FormatAlignCenter, title = str("pref_lyrics_align")) {
        ExpressiveConnectedButtonGroup(
            options = listOf(LyricsAlignment.LEFT, LyricsAlignment.CENTER, LyricsAlignment.RIGHT),
            selectedOption = knobs.alignment,
            onOptionSelected = { knobs.alignment = it },
            iconProvider = {
                val icon = when (it) {
                    LyricsAlignment.LEFT -> Icons.AutoMirrored.Rounded.FormatAlignLeft
                    LyricsAlignment.CENTER -> Icons.Rounded.FormatAlignCenter
                    LyricsAlignment.RIGHT -> Icons.AutoMirrored.Rounded.FormatAlignRight
                }
                Icon(icon, null, modifier = Modifier.size(16.dp))
            },
            labelProvider = {
                ChoiceLabel(
                    when (it) {
                        LyricsAlignment.LEFT -> str("align_left")
                        LyricsAlignment.CENTER -> str("align_center_simple")
                        LyricsAlignment.RIGHT -> str("align_right")
                    }
                )
            },
        )
    }
    AnimatedVisibility(knobs.uiStyle == LyricsUiStyle.ENHANCED) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SliderRow(
                icon = Icons.Rounded.FormatLineSpacing,
                title = str("pref_lyrics_line_spacing_title"),
                value = knobs.lineSpacing,
                valueText = "${knobs.lineSpacing.roundToInt()} dp",
                range = 0f..knobs.maxLineSpacing,
                steps = (knobs.maxLineSpacing / 2f).toInt() - 1,
                onChange = { knobs.lineSpacing = it },
                onReset = { knobs.lineSpacing = 0f },
            )
            SliderRow(
                icon = Icons.Rounded.ZoomOutMap,
                title = str("pref_lyrics_active_scale_title"),
                subtitle = str("pref_lyrics_active_scale_desc"),
                value = knobs.activeScale,
                valueText = "${(knobs.activeScale * 100).roundToInt()}%",
                range = 1f..1.3f,
                steps = 5,
                onChange = { knobs.activeScale = it },
                onReset = { knobs.activeScale = 1f },
            )
            SliderRow(
                icon = Icons.Rounded.SwapHoriz,
                title = str("pref_lyrics_horizontal_margin_title"),
                value = knobs.horizontalMargin,
                valueText = "${knobs.horizontalMargin.roundToInt()} dp",
                range = 0f..knobs.maxHorizontalMargin,
                steps = (knobs.maxHorizontalMargin / 8f).toInt() - 1,
                onChange = { knobs.horizontalMargin = it },
                onReset = { knobs.horizontalMargin = 0f },
            )
            SliderRow(
                icon = Icons.Rounded.VerticalAlignCenter,
                title = str("pref_lyrics_vertical_offset_title"),
                value = knobs.verticalOffset,
                valueText = "${(knobs.verticalOffset * 100).roundToInt()}%",
                range = 0.2f..0.6f,
                steps = 19,
                onChange = { knobs.verticalOffset = it },
                onReset = { knobs.verticalOffset = 0.38f },
            )
        }
    }
    TextButton(
        shapes = ButtonDefaults.shapes(),
        onClick = { knobs.resetTypography() },
        modifier = Modifier.align(Alignment.End),
    ) {
        Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(str("pref_lyrics_reset_typography"))
    }
}

@Composable
private fun ColumnScope.FeaturesTab(
    viewModel: PlayerViewModel,
    isFullScreen: Boolean,
    onPickLanguage: () -> Unit,
    onOpenSources: () -> Unit,
) {
    val prefs = viewModel.playerPrefs
    var preferLocal by remember { mutableStateOf(prefs.getLyricsPreferLocal()) }
    var translationOn by remember { mutableStateOf(prefs.getLyricsTranslationEnabled()) }

    SectionTitle(str("lyrics_content_title"))
    SwitchRow(
        icon = Icons.Rounded.Mic,
        title = str("pref_lyrics_word_sync"),
        subtitle = str("pref_lyrics_word_sync_sub"),
        checked = viewModel.isWordSyncEnabled,
        onChange = { viewModel.toggleWordSync(it) },
    )
    AnimatedVisibility(viewModel.isWordSyncEnabled) {
        SwitchRow(
            icon = Icons.Rounded.AutoAwesome,
            title = str("pref_lyrics_apple_effect"),
            subtitle = str("pref_lyrics_apple_effect_sub"),
            checked = viewModel.isAppleMusicEffectEnabled,
            onChange = { viewModel.toggleAppleMusicEffect(it) },
        )
    }
    SwitchRow(
        icon = Icons.Rounded.Groups,
        title = str("pref_lyrics_duet_title"),
        subtitle = str("pref_lyrics_duet_desc"),
        checked = viewModel.isDuetViewEnabled,
        onChange = { viewModel.toggleDuetView(it) },
    )
    SwitchRow(
        icon = Icons.Rounded.Subtitles,
        title = str("pref_lyrics_one_line_title"),
        subtitle = str("pref_lyrics_one_line_sub"),
        checked = viewModel.oneLineLyricsEnabled,
        onChange = { viewModel.updateOneLineLyricsEnabled(it) },
    )
    SwitchRow(
        icon = Icons.Rounded.RecordVoiceOver,
        title = str("pref_lyrics_backing_title"),
        subtitle = str("pref_lyrics_backing_sub"),
        checked = viewModel.lyricsSplitBackingVocals,
        onChange = { viewModel.updateLyricsSplitBackingVocals(it) },
    )
    SwitchRow(
        icon = Icons.Rounded.Animation,
        title = str("pref_lyrics_reveal_words_title"),
        subtitle = str("pref_lyrics_reveal_words_sub"),
        checked = viewModel.lyricsRevealWords,
        onChange = { viewModel.updateLyricsRevealWords(it) },
    )
    SwitchRow(
        icon = Icons.Rounded.Abc,
        title = str("pref_lyrics_romanization"),
        subtitle = str("pref_lyrics_romanization_sub"),
        checked = viewModel.isRomanizationEnabled,
        onChange = { viewModel.toggleRomanization(it) },
    )
    SwitchRow(
        icon = Icons.Rounded.Translate,
        title = str("pref_lyrics_translation_title"),
        subtitle = str("pref_lyrics_translation_sub"),
        checked = translationOn,
        onChange = {
            translationOn = it
            viewModel.toggleLyricsTranslation(it)
        },
    )
    AnimatedVisibility(translationOn) {
        ClickRow(
            icon = Icons.Rounded.Language,
            title = str("pref_lyrics_translation_lang"),
            value = prefs.getLyricsTranslationLang().uppercase(),
            onClick = onPickLanguage,
        )
    }

    SectionTitle(str("pref_lyrics_providers_category"))
    val enabledSources = PreferredLyricsProvider.entries.count { prefs.getLyricsProviderEnabled(it) }
    ClickRow(
        icon = Icons.Rounded.SwapVert,
        title = str("pref_lyrics_order"),
        value = "$enabledSources / ${PreferredLyricsProvider.entries.size}",
        onClick = onOpenSources,
    )
    SwitchRow(
        icon = Icons.Rounded.FolderOpen,
        title = str("pref_lyrics_local"),
        subtitle = str("pref_lyrics_local_sub"),
        checked = preferLocal,
        onChange = {
            preferLocal = it
            prefs.setLyricsPreferLocal(it)
        },
    )

    SectionTitle(str("lyrics_quick_scrolling"))
    if (viewModel.isPlainAutoScrollEnabled) {
        val perTrack = viewModel.trackAutoScrollSpeed != null
        val speed = viewModel.effectivePlainAutoScrollSpeed
        SliderRow(
            icon = Icons.Rounded.Speed,
            title = str("pref_lyrics_autoscroll_speed"),
            value = speed,
            valueText = autoScrollSpeedText(speed),
            range = 0.25f..4f,
            steps = 14,
            onChange = { if (perTrack) viewModel.setTrackAutoScrollSpeed(it) else viewModel.updatePlainAutoScrollSpeed(it) },
            onReset = { if (perTrack) viewModel.setTrackAutoScrollSpeed(1.5f) else viewModel.updatePlainAutoScrollSpeed(1.5f) },
        )
        SwitchRow(
            icon = Icons.Rounded.MusicNote,
            title = str("pref_lyrics_speed_this_track"),
            subtitle = str("pref_lyrics_speed_this_track_sub"),
            checked = perTrack,
            onChange = { on -> if (on) viewModel.setTrackAutoScrollSpeed(speed) else viewModel.clearTrackAutoScrollSpeed() },
        )
    }
}

// ─── Rows ────────────────────────────────────────────────────────

@Composable
private fun QuickCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    }
}

@Composable
private fun RowIcon(icon: ImageVector, large: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(if (large) 44.dp else 38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(if (large) 24.dp else 20.dp))
        }
    }
}

@Composable
private fun ValuePill(text: String, isActive: Boolean = true) {
    Surface(
        shape = CircleShape,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun ChoiceLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RowTitle(icon: ImageVector, title: String, subtitle: String?, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!checked) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            RowTitle(icon, title, subtitle) {
                Spacer(Modifier.width(10.dp))
                SettingsSwitch(checked = checked, onCheckedChange = onChange)
            }
        }
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            RowTitle(icon, title, null) {
                ValuePill(value)
                Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    aside: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    QuickCard {
        RowTitle(icon, title, null) { aside?.invoke() }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
    subtitle: String? = null,
) {
    QuickCard {
        RowTitle(icon, title, subtitle) {
            ValuePill(valueText)
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onReset, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.RestartAlt, str("pref_lyrics_reset"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth().padding(start = 52.dp, top = 4.dp),
        )
    }
}

@Composable
private fun TranslationLanguageDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val prefs = viewModel.playerPrefs
    var selected by remember { mutableStateOf(prefs.getLyricsTranslationLang()) }
    val systemCode = java.util.Locale.getDefault().language
    val languages = remember {
        val all = java.util.Locale.getISOLanguages()
            .map { code ->
                val locale = java.util.Locale(code)
                code to locale.getDisplayLanguage(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            }
            .filter { it.second.isNotBlank() && it.first.length == 2 }
            .distinctBy { it.first }
            .sortedBy { it.second }
        val system = all.find { it.first == systemCode }
        listOfNotNull(system?.let { it.first to "${it.second} (${str("theme_system")})" }) + all.filter { it.first != systemCode }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("pref_lyrics_translation_lang")) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(languages) { (code, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = code
                                viewModel.setLyricsTranslationLanguage(code)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == code, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(str("btn_cancel")) } },
    )
}

// ─── The view being edited ───────────────────────────────────────

private class ModeKnobs(private val vm: PlayerViewModel, private val isFullScreen: Boolean, private val isSidebar: Boolean) {
    private fun <T> pick(fullScreen: T, sidebar: T, central: T): T = when {
        isFullScreen -> fullScreen
        isSidebar -> sidebar
        else -> central
    }

    val defaultFontSize: Float get() = pick(42f, 22f, 26f)
    val maxLineSpacing: Float get() = pick(64f, 48f, 48f)
    val maxHorizontalMargin: Float get() = pick(160f, 64f, 64f)

    var fontSize: Float
        get() = pick(vm.lyricsFullScreenFontSize, vm.lyricsSidebarFontSize, vm.lyricsFontSize)
        set(v) = pick({ vm.updateLyricsFullScreenFontSize(v) }, { vm.updateLyricsSidebarFontSize(v) }, { vm.updateLyricsFontSize(v) })()

    var uiStyle: LyricsUiStyle
        get() = pick(vm.lyricsFullScreenUiStyle, vm.lyricsSidebarUiStyle, vm.lyricsUiStyle)
        set(v) = pick({ vm.updateLyricsFullScreenUiStyle(v) }, { vm.updateLyricsSidebarUiStyle(v) }, { vm.updateLyricsUiStyle(v) })()

    var alignment: LyricsAlignment
        get() = pick(vm.lyricsFullScreenAlignment, vm.lyricsSidebarAlignment, vm.lyricsAlignment)
        set(v) = pick({ vm.updateLyricsFullScreenAlignment(v) }, { vm.updateLyricsSidebarAlignment(v) }, { vm.updateLyricsAlignment(v) })()

    var displayStyle: LyricsDisplayStyle
        get() = pick(vm.lyricsFullScreenDisplayStyle, vm.lyricsSidebarDisplayStyle, vm.lyricsDisplayStyle)
        set(v) = pick({ vm.updateLyricsFullScreenDisplayStyle(v) }, { vm.updateLyricsSidebarDisplayStyle(v) }, { vm.updateLyricsDisplayStyle(v) })()

    var lineSpacing: Float
        get() = pick(vm.lyricsFullScreenLineSpacing, vm.lyricsSidebarLineSpacing, vm.lyricsLineSpacing)
        set(v) = pick({ vm.updateLyricsFullScreenLineSpacing(v) }, { vm.updateLyricsSidebarLineSpacing(v) }, { vm.updateLyricsLineSpacing(v) })()

    var horizontalMargin: Float
        get() = pick(vm.lyricsFullScreenHorizontalMargin, vm.lyricsSidebarHorizontalMargin, vm.lyricsHorizontalMargin)
        set(v) = pick({ vm.updateLyricsFullScreenHorizontalMargin(v) }, { vm.updateLyricsSidebarHorizontalMargin(v) }, { vm.updateLyricsHorizontalMargin(v) })()

    var verticalOffset: Float
        get() = pick(vm.lyricsFullScreenVerticalOffset, vm.lyricsSidebarVerticalOffset, vm.lyricsVerticalOffset)
        set(v) = pick({ vm.updateLyricsFullScreenVerticalOffset(v) }, { vm.updateLyricsSidebarVerticalOffset(v) }, { vm.updateLyricsVerticalOffset(v) })()

    var activeScale: Float
        get() = pick(vm.lyricsFullScreenActiveScale, vm.lyricsSidebarActiveScale, vm.lyricsActiveScale)
        set(v) = pick({ vm.updateLyricsFullScreenActiveScale(v) }, { vm.updateLyricsSidebarActiveScale(v) }, { vm.updateLyricsActiveScale(v) })()

    var lineBlur: Boolean
        get() = pick(vm.lyricsFullScreenLineBlurEnabled, vm.lyricsSidebarLineBlurEnabled, vm.lyricsLineBlurEnabled)
        set(v) = pick({ vm.updateLyricsFullScreenLineBlurEnabled(v) }, { vm.updateLyricsSidebarLineBlurEnabled(v) }, { vm.updateLyricsLineBlurEnabled(v) })()

    fun resetTypography() = vm.resetLyricsTypography(isFullScreen = isFullScreen, isSidebar = isSidebar)
}

@Composable
private fun bgStyleLabel(style: FullPlayerBgStyle): String = when (style) {
    FullPlayerBgStyle.APPLE_MUSIC -> str("full_player_bg_apple_music")
    FullPlayerBgStyle.BLUR -> str("full_player_bg_blur")
    FullPlayerBgStyle.GRADIENT -> str("full_player_bg_gradient")
    FullPlayerBgStyle.PURE_BLACK -> str("full_player_bg_pure_black")
}

@Composable
private fun infoAlignLabel(align: FullPlayerInfoAlign): String = when (align) {
    FullPlayerInfoAlign.START -> str("full_player_align_left")
    FullPlayerInfoAlign.CENTER -> str("full_player_align_centre")
    FullPlayerInfoAlign.END -> str("full_player_align_right")
}

@Composable
private fun heartSideLabel(side: FullPlayerHeartSide): String = when (side) {
    FullPlayerHeartSide.START -> str("full_player_align_left")
    FullPlayerHeartSide.END -> str("full_player_align_right")
}

@Composable
private fun layoutLabel(layout: FullPlayerLayout): String = when (layout) {
    FullPlayerLayout.LYRICS_RIGHT -> str("full_player_layout_right")
    FullPlayerLayout.LYRICS_LEFT -> str("full_player_layout_left")
    FullPlayerLayout.LYRICS_CENTRED -> str("full_player_layout_centred")
    FullPlayerLayout.COVER_AND_LINE -> str("full_player_layout_single_line")
}

private fun autoScrollSpeedText(speed: Float): String {
    val rounded = kotlin.math.round(speed * 100f) / 100f
    return (if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()) + "×"
}
