package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerActionButtonSlot
import com.alananasss.kittytune.data.local.PlayerDesign
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerProgressMode
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.data.local.WaveformColorMode
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.MenuTiles
import com.alananasss.kittytune.ui.player.slider.PlayerSlider
import kotlin.math.roundToInt

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
    var showRemainingTime by remember { mutableStateOf(prefs.getShowRemainingTime()) }
    var seekWheelSeconds by remember { mutableFloatStateOf(prefs.getSeekWheelSeconds()) }

    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }

    var waveformColorMode by remember { mutableStateOf(prefs.getWaveformColorMode()) }
    var commentsPopup by remember { mutableStateOf(prefs.getWaveformCommentsPopupEnabled()) }
    var reactionsBar by remember { mutableStateOf(prefs.getSoundCloudReactionsBarEnabled()) }
    var parallax by remember { mutableStateOf(prefs.getSoundCloudParallaxEnabled()) }

    val slotCount = if (currentDesign == PlayerDesign.SOUNDCLOUD) 5 else 4
    var slots by remember(currentDesign) {
        mutableStateOf(List(slotCount) { i -> prefs.getSlotForDesign(currentDesign, i) })
    }

    var selectedSlotToEdit by remember { mutableIntStateOf(-1) }
    var showWaveformColorDialog by remember { mutableStateOf(false) }

    var previewSliderProgress by remember { mutableFloatStateOf(0.42f) }
    var isPreviewPlaying by remember { mutableStateOf(true) }

    SettingsScaffold(
        title = stringResource(R.string.pref_player_design),
        subtitle = stringResource(R.string.settings_page_player_sub),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Player Design Selection (2x2 Cards Grid)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SettingsGroupTitle(stringResource(R.string.pref_player_design))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setup_player_design_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 2x2 Grid of Player Designs
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PlayerDesignOptionCard(
                                        title = stringResource(R.string.setup_player_design_pixel),
                                        subtitle = stringResource(R.string.player_design_pixel_desc),
                                        icon = Icons.Rounded.Smartphone,
                                        isSelected = currentDesign == PlayerDesign.PIXEL_PLAYER,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            currentDesign = PlayerDesign.PIXEL_PLAYER
                                            prefs.setPlayerDesign(PlayerDesign.PIXEL_PLAYER)
                                            slots = List(4) { i -> prefs.getSlotForDesign(PlayerDesign.PIXEL_PLAYER, i) }
                                            sliderStyle = prefs.getPlayerSliderStyle()
                                            onUpdated()
                                        }
                                    )

                                    PlayerDesignOptionCard(
                                        title = stringResource(R.string.setup_player_design_soundcloud),
                                        subtitle = stringResource(R.string.player_design_soundcloud_desc),
                                        icon = Icons.Rounded.GraphicEq,
                                        isSelected = currentDesign == PlayerDesign.SOUNDCLOUD,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            currentDesign = PlayerDesign.SOUNDCLOUD
                                            prefs.setPlayerDesign(PlayerDesign.SOUNDCLOUD)
                                            slots = List(5) { i -> prefs.getSlotForDesign(PlayerDesign.SOUNDCLOUD, i) }
                                            onUpdated()
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PlayerDesignOptionCard(
                                        title = stringResource(R.string.setup_player_design_modern),
                                        subtitle = stringResource(R.string.player_design_modern_desc),
                                        icon = Icons.Rounded.AutoAwesome,
                                        isSelected = currentDesign == PlayerDesign.MODERN,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            currentDesign = PlayerDesign.MODERN
                                            prefs.setPlayerDesign(PlayerDesign.MODERN)
                                            slots = List(4) { i -> prefs.getSlotForDesign(PlayerDesign.MODERN, i) }
                                            modernProgressMode = prefs.getPlayerProgressMode()
                                            sliderStyle = prefs.getPlayerSliderStyle()
                                            onUpdated()
                                        }
                                    )

                                    PlayerDesignOptionCard(
                                        title = stringResource(R.string.setup_player_design_classic),
                                        subtitle = stringResource(R.string.player_design_classic_desc),
                                        icon = Icons.Rounded.LinearScale,
                                        isSelected = currentDesign == PlayerDesign.CLASSIC,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            currentDesign = PlayerDesign.CLASSIC
                                            prefs.setPlayerDesign(PlayerDesign.CLASSIC)
                                            slots = List(4) { i -> prefs.getSlotForDesign(PlayerDesign.CLASSIC, i) }
                                            sliderStyle = prefs.getPlayerSliderStyle()
                                            onUpdated()
                                        }
                                    )
                                }
                            }

                            // Sub-selector: Progress Mode for Modern Player
                            AnimatedVisibility(visible = currentDesign == PlayerDesign.MODERN) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_style_group),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ExpressiveConnectedButtonGroup(
                                        options = listOf(PlayerProgressMode.CLASSIC_BAR, PlayerProgressMode.HYBRID_WAVEFORM),
                                        selectedOption = modernProgressMode,
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
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
                            }
                        }
                    }
                }
            }

            // 2. Slider Style Section (Uncompressed, wide 2x2 grid + Live Interactive Hero Preview)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SettingsGroupTitle(stringResource(R.string.pref_slider_style))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pref_slider_style_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Hero Live Preview Container
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.SpaceBetween,
                                         verticalAlignment = Alignment.CenterVertically
                                     ) {
                                         Row(
                                             verticalAlignment = Alignment.CenterVertically,
                                             horizontalArrangement = Arrangement.spacedBy(10.dp),
                                             modifier = Modifier.weight(1f)
                                         ) {
                                             Surface(
                                                 modifier = Modifier.size(36.dp),
                                                 shape = RoundedCornerShape(10.dp),
                                                 color = MaterialTheme.colorScheme.primaryContainer
                                             ) {
                                                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                     Icon(
                                                         imageVector = Icons.Rounded.MusicNote,
                                                         contentDescription = null,
                                                         tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                         modifier = Modifier.size(20.dp)
                                                     )
                                                 }
                                             }
                                             Column(modifier = Modifier.weight(1f, fill = false)) {
                                                 Text(
                                                     text = "party addict +nosgov (kojo)",
                                                     style = MaterialTheme.typography.titleSmall,
                                                     fontWeight = FontWeight.Bold,
                                                     maxLines = 1,
                                                     overflow = TextOverflow.Ellipsis
                                                 )
                                                 Text(
                                                     text = "kets4eki, Nosgov",
                                                     style = MaterialTheme.typography.bodySmall,
                                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                     maxLines = 1,
                                                     overflow = TextOverflow.Ellipsis
                                                 )
                                             }
                                         }
                                         Surface(
                                             shape = CircleShape,
                                             color = MaterialTheme.colorScheme.primary,
                                             modifier = Modifier.size(32.dp),
                                             onClick = { isPreviewPlaying = !isPreviewPlaying }
                                         ) {
                                             Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                 Icon(
                                                     imageVector = if (isPreviewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                     contentDescription = null,
                                                     tint = MaterialTheme.colorScheme.onPrimary,
                                                     modifier = Modifier.size(18.dp)
                                                 )
                                             }
                                         }
                                     }

                                     Spacer(Modifier.height(12.dp))

                                     PlayerSlider(
                                         value = previewSliderProgress,
                                         onValueChange = { previewSliderProgress = it },
                                         sliderStyle = sliderStyle,
                                         isPlaying = isPreviewPlaying,
                                         modifier = Modifier.fillMaxWidth()
                                     )

                                    Spacer(Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val totalSec = 154
                                        val curSec = (previewSliderProgress * totalSec).toInt()
                                        val remSec = totalSec - curSec
                                        Text(
                                            text = String.format("%02d:%02d", curSec / 60, curSec % 60),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (showRemainingTime) {
                                                String.format("-%02d:%02d", remSec / 60, remSec % 60)
                                            } else {
                                                String.format("%02d:%02d", totalSec / 60, totalSec % 60)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 2x2 Grid of Slider Styles (spacious, wide preview cards)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SliderOptionCard(
                                        title = stringResource(R.string.slider_style_bar),
                                        style = PlayerSliderStyle.BAR,
                                        isSelected = sliderStyle == PlayerSliderStyle.BAR,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            sliderStyle = PlayerSliderStyle.BAR
                                            prefs.setPlayerSliderStyle(PlayerSliderStyle.BAR)
                                            onUpdated()
                                        }
                                    )

                                    SliderOptionCard(
                                        title = stringResource(R.string.slider_style_wavy),
                                        style = PlayerSliderStyle.WAVY,
                                        isSelected = sliderStyle == PlayerSliderStyle.WAVY,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            sliderStyle = PlayerSliderStyle.WAVY
                                            prefs.setPlayerSliderStyle(PlayerSliderStyle.WAVY)
                                            onUpdated()
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SliderOptionCard(
                                        title = stringResource(R.string.slider_style_slim),
                                        style = PlayerSliderStyle.SLIM,
                                        isSelected = sliderStyle == PlayerSliderStyle.SLIM,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            sliderStyle = PlayerSliderStyle.SLIM
                                            prefs.setPlayerSliderStyle(PlayerSliderStyle.SLIM)
                                            onUpdated()
                                        }
                                    )

                                    SliderOptionCard(
                                        title = stringResource(R.string.slider_style_squiggly),
                                        style = PlayerSliderStyle.SQUIGGLY,
                                        isSelected = sliderStyle == PlayerSliderStyle.SQUIGGLY,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            sliderStyle = PlayerSliderStyle.SQUIGGLY
                                            prefs.setPlayerSliderStyle(PlayerSliderStyle.SQUIGGLY)
                                            onUpdated()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Playback Controls & Sensitivity (M3 Grouped Settings)
            item {
                SettingsGroup(
                    title = stringResource(R.string.player_advanced_title),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_show_remaining_time),
                                subtitle = stringResource(R.string.pref_show_remaining_time_desc),
                                icon = Icons.Rounded.Timer,
                                hasSwitch = true,
                                switchState = showRemainingTime,
                                onSwitchChange = {
                                    showRemainingTime = it
                                    prefs.setShowRemainingTime(it)
                                    onUpdated()
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_seek_wheel),
                                subtitle = stringResource(R.string.player_advanced_desc),
                                trailingText = "${seekWheelSeconds.roundToInt()}s",
                                icon = Icons.Rounded.Timelapse,
                                hasSlider = true,
                                sliderValue = seekWheelSeconds,
                                sliderRange = 1f..30f,
                                onSliderChange = {
                                    seekWheelSeconds = it
                                    prefs.setSeekWheelSeconds(it)
                                    onUpdated()
                                }
                            )
                        }
                    )
                )
            }

            // 4. Display & Visual Effects (M3 Grouped Settings)
            item {
                val visualItems = buildList<@Composable (androidx.compose.ui.graphics.Shape) -> Unit> {
                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_animated_covers),
                            subtitle = stringResource(R.string.pref_animated_covers_desc),
                            icon = Icons.Rounded.Movie,
                            hasSwitch = true,
                            switchState = animatedCovers,
                            onSwitchChange = {
                                animatedCovers = it
                                prefs.setAnimatedCoversEnabled(it)
                                onUpdated()
                            }
                        )
                    }

                    if (animatedCovers) {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_animated_covers_fade_ui),
                                subtitle = stringResource(R.string.pref_animated_covers_fade_ui_desc),
                                icon = Icons.Rounded.BlurLinear,
                                hasSwitch = true,
                                switchState = animatedCoversFadeUi,
                                onSwitchChange = {
                                    animatedCoversFadeUi = it
                                    prefs.setAnimatedCoversFadeUiEnabled(it)
                                    onUpdated()
                                }
                            )
                        }
                    }

                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_animated_artist_profiles),
                            subtitle = stringResource(R.string.pref_animated_artist_profiles_desc),
                            icon = Icons.Rounded.AccountBox,
                            hasSwitch = true,
                            switchState = animatedArtistProfiles,
                            onSwitchChange = {
                                animatedArtistProfiles = it
                                prefs.setAnimatedArtistProfilesEnabled(it)
                                onUpdated()
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
                                icon = Icons.Rounded.Palette,
                                onClick = { showWaveformColorDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_opt_comment_bubbles_title),
                                subtitle = stringResource(R.string.player_opt_comment_bubbles_subtitle),
                                icon = Icons.AutoMirrored.Rounded.Comment,
                                hasSwitch = true,
                                switchState = commentsPopup,
                                onSwitchChange = {
                                    commentsPopup = it
                                    prefs.setWaveformCommentsPopupEnabled(it)
                                    onUpdated()
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_opt_reactions_bar_title),
                                subtitle = stringResource(R.string.player_opt_reactions_bar_subtitle),
                                icon = Icons.Rounded.ThumbUp,
                                hasSwitch = true,
                                switchState = reactionsBar,
                                onSwitchChange = {
                                    reactionsBar = it
                                    prefs.setSoundCloudReactionsBarEnabled(it)
                                    onUpdated()
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_opt_parallax_title),
                                subtitle = stringResource(R.string.player_opt_parallax_subtitle),
                                icon = Icons.Rounded.Layers,
                                hasSwitch = true,
                                switchState = parallax,
                                onSwitchChange = {
                                    parallax = it
                                    prefs.setSoundCloudParallaxEnabled(it)
                                    onUpdated()
                                }
                            )
                        }
                    }
                }

                SettingsGroup(
                    title = stringResource(R.string.player_visual_options_group),
                    items = visualItems
                )
            }

            // 5. Action Buttons Configuration (Slots) (M3 Grouped Settings)
            item {
                val barTitle = if (slotCount == 5) {
                    stringResource(R.string.player_action_bar_5_title)
                } else {
                    stringResource(R.string.player_action_bar_4_title)
                }
                val barDesc = if (slotCount == 5) {
                    stringResource(R.string.player_action_bar_5_desc)
                } else {
                    stringResource(R.string.player_action_bar_4_desc)
                }

                val actionItems = buildList<@Composable (androidx.compose.ui.graphics.Shape) -> Unit> {
                    repeat(slots.size) { index ->
                        val slot = slots[index]
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_slot_n, index + 1),
                                subtitle = stringResource(slot.titleRes),
                                icon = getSlotIcon(slot),
                                trailingText = stringResource(R.string.player_slot_change),
                                onClick = { selectedSlotToEdit = index }
                            )
                        }
                    }
                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.btn_reset),
                            subtitle = barDesc,
                            icon = Icons.Rounded.RestartAlt,
                            onClick = {
                                prefs.resetDesignCustomization(currentDesign)
                                slots = List(slotCount) { i -> prefs.getSlotForDesign(currentDesign, i) }
                                sliderStyle = prefs.getPlayerSliderStyle()
                                onUpdated()
                            }
                        )
                    }
                }

                SettingsGroup(
                    title = barTitle,
                    items = actionItems
                )
            }

            // 6. Track Menu Sheet Tiles (M3 Grouped Settings)
            item {
                MenuTilesGroup(
                    title = stringResource(R.string.menu_tiles_track),
                    menu = PlayerPreferences.MENU_TRACK,
                    catalogue = MenuTiles.TRACK,
                    prefs = prefs
                )
            }
        }
    }

    // Dialog: Edit Button Slot
    if (selectedSlotToEdit >= 0) {
        val allSlots = PlayerActionButtonSlot.entries
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
                TextButton(onClick = { selectedSlotToEdit = -1 }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Dialog: Waveform Color Mode
    if (showWaveformColorDialog) {
        val modes = WaveformColorMode.entries
        AlertDialog(
            onDismissRequest = { showWaveformColorDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_waveform_color_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    modes.forEachIndexed { idx, mode ->
                        val isSelected = waveformColorMode == mode
                        SettingsItem(
                            shape = getSettingsShape(modes.size, idx),
                            title = when (mode) {
                                WaveformColorMode.SOUNDCLOUD -> stringResource(R.string.waveform_color_soundcloud)
                                WaveformColorMode.COVER_ART -> stringResource(R.string.waveform_color_cover_art)
                                WaveformColorMode.APP_THEME -> stringResource(R.string.waveform_color_app_theme)
                                WaveformColorMode.CUSTOM -> stringResource(R.string.waveform_color_custom)
                            },
                            icon = Icons.Rounded.Palette,
                            trailingText = if (isSelected) stringResource(R.string.player_slot_active) else null,
                            onClick = {
                                waveformColorMode = mode
                                prefs.setWaveformColorMode(mode)
                                showWaveformColorDialog = false
                                onUpdated()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWaveformColorDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * 2x2 Selection Card for Player Designs
 */
@Composable
private fun PlayerDesignOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        label = "designCardContainer"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * Visual Interactive Slider Preview Card
 */
@Composable
private fun SliderOptionCard(
    title: String,
    style: PlayerSliderStyle,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        label = "sliderOptionContainer"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                contentAlignment = Alignment.Center
            ) {
                PlayerSlider(
                    value = 0.5f,
                    onValueChange = {},
                    sliderStyle = style,
                    isPlaying = true,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                )
            }
        }
    }
}

/**
 * Menu des morceaux (3-dots sheet menu)
 */
@Composable
private fun MenuTilesGroup(
    title: String,
    menu: String,
    catalogue: List<MenuTiles.Tile>,
    prefs: PlayerPreferences
) {
    var hidden by remember(menu) { mutableStateOf(prefs.getHiddenMenuTiles(menu)) }

    val menuItems = buildList<@Composable (androidx.compose.ui.graphics.Shape) -> Unit> {
        catalogue.forEach { tile ->
            val isEnabled = tile.id !in hidden
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = stringResource(tile.labelRes),
                    icon = MenuTiles.tileIcon(tile.id),
                    hasSwitch = true,
                    switchState = isEnabled,
                    onSwitchChange = { on ->
                        val nextHidden = if (on) hidden - tile.id else hidden + tile.id
                        hidden = nextHidden
                        prefs.setHiddenMenuTiles(menu, nextHidden)
                    }
                )
            }
        }
        add { shape ->
            SettingsItem(
                shape = shape,
                title = stringResource(R.string.menu_tiles_reset),
                subtitle = stringResource(R.string.menu_tiles_desc),
                icon = Icons.Rounded.RestartAlt,
                onClick = {
                    prefs.resetMenuTiles(menu)
                    hidden = emptySet()
                }
            )
        }
    }

    SettingsGroup(
        title = title,
        items = menuItems
    )
}

private fun getSlotIcon(slot: PlayerActionButtonSlot): ImageVector = when (slot) {
    PlayerActionButtonSlot.LIKE -> Icons.Rounded.Favorite
    PlayerActionButtonSlot.COMMENTS -> Icons.AutoMirrored.Rounded.Comment
    PlayerActionButtonSlot.SHARE -> Icons.Rounded.Share
    PlayerActionButtonSlot.QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
    PlayerActionButtonSlot.AUDIO_FX -> Icons.Default.Equalizer
    PlayerActionButtonSlot.SHUFFLE -> Icons.Rounded.Shuffle
    PlayerActionButtonSlot.REPEAT -> Icons.Rounded.Repeat
    PlayerActionButtonSlot.LYRICS -> Icons.Rounded.Description
    PlayerActionButtonSlot.FULLSCREEN_LYRICS -> Icons.Rounded.OpenInFull
    PlayerActionButtonSlot.SLEEP_TIMER -> Icons.Rounded.Bedtime
    PlayerActionButtonSlot.HAPTICS -> Icons.Rounded.Vibration
    PlayerActionButtonSlot.MORE -> Icons.Rounded.MoreVert
    PlayerActionButtonSlot.NONE -> Icons.Rounded.Block
}
