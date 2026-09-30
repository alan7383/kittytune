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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.DragHandle
import com.alananasss.kittytune.ui.common.SettingsSwitch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.PlaybackService
import com.alananasss.kittytune.data.local.NotificationExtraButton
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

    var notifExtraButton by remember { mutableStateOf(prefs.getNotificationExtraButton()) }
    var showNotifExtraButtonDialog by remember { mutableStateOf(false) }
    var miniPlayerSwipeAction by remember { mutableStateOf(prefs.getMiniPlayerSwipeAction()) }
    var showMiniPlayerSwipeActionDialog by remember { mutableStateOf(false) }

    var previewSliderProgress by remember { mutableFloatStateOf(0.42f) }
    var isPreviewPlaying by remember { mutableStateOf(true) }

    val view = LocalView.current
    val listState = rememberLazyListState()

    val catalogue = remember { MenuTiles.catalogue(PlayerPreferences.MENU_TRACK) }
    val initialOrder = remember {
        val stored = prefs.getMenuTileOrder(PlayerPreferences.MENU_TRACK)
        if (stored.isEmpty()) {
            catalogue.map { it.id }
        } else {
            stored.filter { id -> catalogue.any { it.id == id } } +
                catalogue.map { it.id }.filter { it !in stored }
        }
    }
    val tileOrder = remember { mutableStateListOf<String>().apply { addAll(initialOrder) } }
    var hiddenTiles by remember { mutableStateOf(prefs.getHiddenMenuTiles(PlayerPreferences.MENU_TRACK)) }

    fun persistTileOrder() {
        prefs.setMenuTileOrder(PlayerPreferences.MENU_TRACK, tileOrder.toList())
        onUpdated()
    }

    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = tileOrder.indexOf(fromKey)
            val toIndex = tileOrder.indexOf(toKey)
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                val moved = tileOrder.removeAt(fromIndex)
                tileOrder.add(toIndex, moved)
                persistTileOrder()
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            }
        }
    )

    SettingsScaffold(
        title = stringResource(R.string.pref_player_design),
        subtitle = stringResource(R.string.settings_page_player_sub),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
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

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PlayerDesignButton(
                                        title = stringResource(R.string.setup_player_design_pixel),
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

                                    PlayerDesignButton(
                                        title = stringResource(R.string.setup_player_design_soundcloud),
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
                                    PlayerDesignButton(
                                        title = stringResource(R.string.setup_player_design_modern),
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

                                    PlayerDesignButton(
                                        title = stringResource(R.string.setup_player_design_classic),
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
                        }
                    )
                )
            }

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

            item {
                val notifItems = listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit> { shape ->
                    SettingsItem(
                        shape = shape,
                        title = stringResource(R.string.pref_notif_extra_button_title),
                        subtitle = stringResource(notifExtraButton.titleRes),
                        icon = getNotifButtonVector(notifExtraButton),
                        iconRes = getNotifButtonIconRes(notifExtraButton),
                        trailingText = stringResource(R.string.player_slot_change),
                        onClick = { showNotifExtraButtonDialog = true }
                    )
                }

                SettingsGroup(
                    title = stringResource(R.string.notif_player_options_group),
                    items = notifItems
                )
            }

            item(key = "mini_player_swipe_settings") {
                val miniItems = listOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit> { shape ->
                    SettingsItem(
                        shape = shape,
                        title = stringResource(R.string.pref_mini_player_swipe_action_title),
                        subtitle = when (miniPlayerSwipeAction) {
                            com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.CHANGE_TRACK ->
                                stringResource(R.string.pref_mini_player_swipe_action_change_track)
                            com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.DISMISS ->
                                stringResource(R.string.pref_mini_player_swipe_action_dismiss)
                        },
                        trailingText = stringResource(R.string.player_slot_change),
                        onClick = { showMiniPlayerSwipeActionDialog = true }
                    )
                }

                SettingsGroup(
                    title = stringResource(R.string.pref_mini_player_title),
                    items = miniItems
                )
            }

            // 6. Track Menu Sheet Tiles (Draggable M3 Grouped Settings)
            item(key = "menu_tiles_header") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.menu_tiles_track))
                    Text(
                        text = stringResource(R.string.menu_tiles_reorder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            itemsIndexed(tileOrder, key = { _, id -> id }) { index, tileId ->
                val tile = catalogue.firstOrNull { it.id == tileId }
                if (tile != null) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)) {
                        ReorderableItem(state = reorderState, key = tileId) { isDragging ->
                            val elevation by animateDpAsState(
                                if (isDragging) 8.dp else 0.dp,
                                label = "tileElevation"
                            )
                            val isEnabled = tileId !in hiddenTiles

                            Surface(
                                shape = if (isDragging) RoundedCornerShape(16.dp) else getSettingsShape(tileOrder.size, index),
                                color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = elevation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 1f else 0f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp)
                                        .padding(start = 4.dp, end = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                },
                                                onDragStopped = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = stringResource(R.string.reorder_handle),
                                            tint = if (isDragging) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = MenuTiles.tileIcon(tile.id),
                                        contentDescription = null,
                                        tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                        modifier = Modifier.size(22.dp)
                                    )

                                    Spacer(Modifier.width(16.dp))

                                    Text(
                                        text = stringResource(tile.labelRes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    SettingsSwitch(
                                        checked = isEnabled,
                                        onCheckedChange = { on ->
                                            val nextHidden = if (on) hiddenTiles - tile.id else hiddenTiles + tile.id
                                            hiddenTiles = nextHidden
                                            prefs.setHiddenMenuTiles(PlayerPreferences.MENU_TRACK, nextHidden)
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            onUpdated()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "menu_tiles_reset") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 14.dp, bottom = 8.dp)
                ) {
                    SettingsItem(
                        shape = RoundedCornerShape(24.dp),
                        title = stringResource(R.string.menu_tiles_reset),
                        subtitle = stringResource(R.string.menu_tiles_desc),
                        icon = Icons.Rounded.RestartAlt,
                        onClick = {
                            prefs.resetMenuTiles(PlayerPreferences.MENU_TRACK)
                            hiddenTiles = prefs.getHiddenMenuTiles(PlayerPreferences.MENU_TRACK)
                            tileOrder.clear()
                            tileOrder.addAll(catalogue.map { it.id })
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onUpdated()
                        }
                    )
                }
            }
        }
    }

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

    if (showNotifExtraButtonDialog) {
        val allOptions = NotificationExtraButton.entries
        AlertDialog(
            onDismissRequest = { showNotifExtraButtonDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_notif_extra_button_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(allOptions.size) { idx ->
                        val option = allOptions[idx]
                        val isSelected = notifExtraButton == option
                        SettingsItem(
                            shape = getSettingsShape(allOptions.size, idx),
                            title = stringResource(option.titleRes),
                            subtitle = stringResource(option.subtitleRes),
                            icon = getNotifButtonVector(option),
                            iconRes = getNotifButtonIconRes(option),
                            trailingText = if (isSelected) stringResource(R.string.player_slot_active) else null,
                            onClick = {
                                notifExtraButton = option
                                prefs.setNotificationExtraButton(option)
                                val intent = Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_FORCE_UPDATE
                                }
                                context.startService(intent)
                                showNotifExtraButtonDialog = false
                                onUpdated()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifExtraButtonDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showMiniPlayerSwipeActionDialog) {
        AlertDialog(
            onDismissRequest = { showMiniPlayerSwipeActionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_mini_player_swipe_action_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                prefs.setMiniPlayerSwipeAction(com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.CHANGE_TRACK)
                                miniPlayerSwipeAction = com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.CHANGE_TRACK
                                showMiniPlayerSwipeActionDialog = false
                                onUpdated()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = miniPlayerSwipeAction == com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.CHANGE_TRACK,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.pref_mini_player_swipe_action_change_track),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.pref_mini_player_swipe_action_change_track_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                prefs.setMiniPlayerSwipeAction(com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.DISMISS)
                                miniPlayerSwipeAction = com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.DISMISS
                                showMiniPlayerSwipeActionDialog = false
                                onUpdated()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = miniPlayerSwipeAction == com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.DISMISS,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.pref_mini_player_swipe_action_dismiss),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.pref_mini_player_swipe_action_dismiss_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMiniPlayerSwipeActionDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerDesignButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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

private fun getNotifButtonIconRes(button: NotificationExtraButton): Int? = when (button) {
    NotificationExtraButton.DISLIKE -> R.drawable.ic_heart_broken
    NotificationExtraButton.SHUFFLE -> R.drawable.rounded_shuffle_24
    NotificationExtraButton.REPEAT -> R.drawable.ic_repeat
    NotificationExtraButton.ADD_TO_LAST_PLAYLIST -> R.drawable.ic_playlist_add
    NotificationExtraButton.HAPTICS -> R.drawable.ic_vibration
    NotificationExtraButton.SHARE -> R.drawable.ic_share
    NotificationExtraButton.DOWNLOAD -> R.drawable.ic_download
    NotificationExtraButton.OFF -> null
}

private fun getNotifButtonVector(button: NotificationExtraButton): ImageVector? = when (button) {
    NotificationExtraButton.OFF -> Icons.Rounded.Block
    else -> null
}
