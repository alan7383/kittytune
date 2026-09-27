package com.alananasss.kittytune.ui.profile

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.WaveformRepository
import com.alananasss.kittytune.data.local.PlayerActionButtonSlot
import com.alananasss.kittytune.data.local.PlayerDesign
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerProgressMode
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.data.local.WaveformColorMode
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.slider.SliderStyleDialog
import com.alananasss.kittytune.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults

/**
 * Player customisation, as one page with every section visible.
 *
 * This used to be a bottom sheet behind an "Edit" row, and the sheet branched on the chosen design:
 * picking SoundCloud replaced the whole body, so the action bar and the visual options moved around
 * depending on what was selected and nothing stayed where it was left. The sections are now fixed
 * and always present - design, style, display, action bar - and only the rows inside "display"
 * follow the design, which is what actually varies.
 */
@Composable
fun PlayerCustomizationScreen(
    onBackClick: () -> Unit,
    onUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var currentDesign by remember { mutableStateOf(prefs.getPlayerDesign()) }
    var modernProgressMode by remember {
        mutableStateOf(
            if (prefs.getPlayerProgressMode() == PlayerProgressMode.SOUNDCLOUD) {
                PlayerProgressMode.CLASSIC_BAR
            } else {
                prefs.getPlayerProgressMode()
            }
        )
    }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }
    var waveformColorMode by remember { mutableStateOf(prefs.getWaveformColorMode()) }
    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }
    var commentsPopup by remember { mutableStateOf(prefs.getWaveformCommentsPopupEnabled()) }
    var reactionsBar by remember { mutableStateOf(prefs.getSoundCloudReactionsBarEnabled()) }
    var parallax by remember { mutableStateOf(prefs.getSoundCloudParallaxEnabled()) }
    var selectedSlotToEdit by remember { mutableStateOf(-1) }
    var showSliderStyleDialog by remember { mutableStateOf(false) }
    var showWaveformColorDialog by remember { mutableStateOf(false) }
    var waveformCustomColor by remember { mutableIntStateOf(prefs.getWaveformCustomColor()) }

    val slotCount = if (currentDesign == PlayerDesign.SOUNDCLOUD) 5 else 4
    var slots by remember(currentDesign) {
        mutableStateOf(List(slotCount) { i -> prefs.getSlotForDesign(currentDesign, i) })
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_player_design),
        subtitle = stringResource(R.string.player_customization_subtitle),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp)
        ) {
            // 1. Which player
            item {
                SettingsGroupTitle(stringResource(R.string.pref_player_design))
                ExpressiveConnectedButtonGroup(
                    options = listOf(
                        PlayerDesign.PIXEL_PLAYER,
                        PlayerDesign.SOUNDCLOUD,
                        PlayerDesign.MODERN,
                        PlayerDesign.CLASSIC
                    ),
                    selectedOption = currentDesign,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    onOptionSelected = {
                        currentDesign = it
                        prefs.setPlayerDesign(it)
                        val newCount = if (it == PlayerDesign.SOUNDCLOUD) 5 else 4
                        slots = List(newCount) { i -> prefs.getSlotForDesign(it, i) }
                        sliderStyle = prefs.getPlayerSliderStyle()
                        modernProgressMode = prefs.getPlayerProgressMode()
                        onUpdated()
                    },
                    labelProvider = { option ->
                        Text(
                            text = when (option) {
                                PlayerDesign.PIXEL_PLAYER -> stringResource(R.string.player_design_pixel)
                                PlayerDesign.SOUNDCLOUD -> stringResource(R.string.player_design_soundcloud)
                                PlayerDesign.MODERN -> stringResource(R.string.player_design_modern)
                                PlayerDesign.CLASSIC -> stringResource(R.string.player_design_classic)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    iconProvider = { option ->
                        when (option) {
                            PlayerDesign.PIXEL_PLAYER -> Icons.Rounded.Smartphone
                            PlayerDesign.SOUNDCLOUD -> Icons.Rounded.GraphicEq
                            PlayerDesign.CLASSIC -> Icons.Rounded.LinearScale
                            else -> null
                        }
                    }
                )
            }

            // 2. How the bar reads
            item {
                SettingsGroupTitle(stringResource(R.string.player_style_group))
                ExpressiveConnectedButtonGroup(
                    options = listOf(PlayerProgressMode.CLASSIC_BAR, PlayerProgressMode.HYBRID_WAVEFORM),
                    selectedOption = modernProgressMode,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    onOptionSelected = {
                        modernProgressMode = it
                        prefs.setPlayerProgressMode(it)
                        onUpdated()
                    },
                    labelProvider = { option ->
                        Text(
                            text = if (option == PlayerProgressMode.HYBRID_WAVEFORM) {
                                stringResource(R.string.player_style_hybrid_short)
                            } else {
                                stringResource(R.string.player_style_classic_short)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }

            // 3. What it looks like
            item {
                SettingsGroupTitle(stringResource(R.string.player_visual_options_group))
                // Built as a list first so every row gets the right shape: the first and last of a
                // group round their outer corners, the ones between do not, and the count changes
                // with the design and with whether the fade-UI row is showing.
                val rows = buildList<@Composable (Shape) -> Unit> {
                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_slider_style),
                            subtitle = sliderStyleLabel(sliderStyle),
                            onClick = { showSliderStyleDialog = true }
                        )
                    }
                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_animated_covers),
                            subtitle = stringResource(R.string.pref_animated_covers_desc),
                            hasSwitch = true,
                            switchState = animatedCovers,
                            onSwitchChange = {
                                animatedCovers = it
                                prefs.setAnimatedCoversEnabled(it)
                            }
                        )
                    }
                    if (animatedCovers) {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_animated_covers_fade_ui),
                                subtitle = stringResource(R.string.pref_animated_covers_fade_ui_desc),
                                hasSwitch = true,
                                switchState = animatedCoversFadeUi,
                                onSwitchChange = {
                                    animatedCoversFadeUi = it
                                    prefs.setAnimatedCoversFadeUiEnabled(it)
                                }
                            )
                        }
                    }
                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_animated_artist_profiles),
                            subtitle = stringResource(R.string.pref_animated_artist_profiles_desc),
                            hasSwitch = true,
                            switchState = animatedArtistProfiles,
                            onSwitchChange = {
                                animatedArtistProfiles = it
                                prefs.setAnimatedArtistProfilesEnabled(it)
                            }
                        )
                    }
                    if (currentDesign == PlayerDesign.SOUNDCLOUD) {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_waveform_color_title),
                                subtitle = when (waveformColorMode) {
                                    WaveformColorMode.SOUNDCLOUD -> stringResource(R.string.waveform_color_soundcloud)
                                    WaveformColorMode.COVER_ART -> stringResource(R.string.waveform_color_cover_art)
                                    WaveformColorMode.APP_THEME -> stringResource(R.string.waveform_color_app_theme)
                                    WaveformColorMode.CUSTOM -> stringResource(R.string.waveform_color_custom)
                                },
                                onClick = { showWaveformColorDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_opt_comment_bubbles_title),
                                subtitle = stringResource(R.string.player_opt_comment_bubbles_subtitle),
                                hasSwitch = true,
                                switchState = commentsPopup,
                                onSwitchChange = {
                                    commentsPopup = it
                                    prefs.setWaveformCommentsPopupEnabled(it)
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_opt_reactions_bar_title),
                                subtitle = stringResource(R.string.player_opt_reactions_bar_subtitle),
                                hasSwitch = true,
                                switchState = reactionsBar,
                                onSwitchChange = {
                                    reactionsBar = it
                                    prefs.setSoundCloudReactionsBarEnabled(it)
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_opt_parallax_title),
                                subtitle = stringResource(R.string.player_opt_parallax_subtitle),
                                hasSwitch = true,
                                switchState = parallax,
                                onSwitchChange = {
                                    parallax = it
                                    prefs.setSoundCloudParallaxEnabled(it)
                                }
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    rows.forEachIndexed { index, row -> row(getSettingsShape(rows.size, index)) }
                }
            }

            // 4. What the buttons do
            item {
                SettingsGroupTitle(
                    stringResource(
                        if (slotCount == 5) R.string.player_action_bar_5_title
                        else R.string.player_action_bar_4_title
                    )
                )
                Text(
                    text = stringResource(R.string.player_action_bar_pixel_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    slots.forEachIndexed { index, slot ->
                        SettingsItem(
                            shape = getSettingsShape(slots.size, index),
                            title = stringResource(R.string.player_slot_n, index + 1),
                            subtitle = stringResource(slot.titleRes),
                            icon = getSlotIcon(slot),
                            trailingText = stringResource(R.string.player_slot_change),
                            onClick = { selectedSlotToEdit = index }
                        )
                    }
                }
            }
        }
    }

    if (showWaveformColorDialog) {
        WaveformColorDialog(
            currentMode = waveformColorMode,
            currentColor = waveformCustomColor,
            onModeSelected = { mode ->
                waveformColorMode = mode
                prefs.setWaveformColorMode(mode)
                onUpdated()
            },
            onColorSelected = { color ->
                waveformCustomColor = color
                prefs.setWaveformCustomColor(color)
                waveformColorMode = WaveformColorMode.CUSTOM
                prefs.setWaveformColorMode(WaveformColorMode.CUSTOM)
                onUpdated()
            },
            onDismiss = { showWaveformColorDialog = false }
        )
    }

    if (showSliderStyleDialog) {
        SliderStyleDialog(
            currentStyle = sliderStyle,
            onStyleSelected = {
                sliderStyle = it
                prefs.setPlayerSliderStyle(it)
                onUpdated()
            },
            onDismiss = { showSliderStyleDialog = false }
        )
    }

    if (selectedSlotToEdit >= 0) {
        val allSlots = PlayerActionButtonSlot.values().toList()
        AlertDialog(
            onDismissRequest = { selectedSlotToEdit = -1 },
            title = {
                Text(
                    text = stringResource(R.string.player_slot_n, selectedSlotToEdit + 1),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(allSlots.size) { idx ->
                        val slotOption = allSlots[idx]
                        val isSelected = slots.getOrNull(selectedSlotToEdit) == slotOption
                        SettingsItem(
                            shape = getSettingsShape(allSlots.size, idx),
                            title = stringResource(slotOption.titleRes),
                            icon = getSlotIcon(slotOption),
                            trailingText = if (isSelected) stringResource(R.string.player_slot_active) else null,
                            onClick = {
                                if (selectedSlotToEdit in slots.indices) {
                                    prefs.setSlotForDesign(currentDesign, selectedSlotToEdit, slotOption)
                                    slots = List(slotCount) { i -> prefs.getSlotForDesign(currentDesign, i) }
                                    onUpdated()
                                }
                                selectedSlotToEdit = -1
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSlotToEdit = -1 }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun sliderStyleLabel(style: com.alananasss.kittytune.data.local.PlayerSliderStyle): String = when (style) {
    com.alananasss.kittytune.data.local.PlayerSliderStyle.BAR -> stringResource(R.string.slider_style_bar)
    com.alananasss.kittytune.data.local.PlayerSliderStyle.WAVY -> stringResource(R.string.slider_style_wavy)
    com.alananasss.kittytune.data.local.PlayerSliderStyle.SLIM -> stringResource(R.string.slider_style_slim)
    com.alananasss.kittytune.data.local.PlayerSliderStyle.SQUIGGLY -> stringResource(R.string.slider_style_squiggly)
}
