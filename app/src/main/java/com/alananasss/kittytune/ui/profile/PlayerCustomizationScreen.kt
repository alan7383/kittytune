package com.alananasss.kittytune.ui.profile

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.graphics.Color as AndroidColor
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.DragHandle
import com.alananasss.kittytune.ui.common.SettingsSwitch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.PlaybackService
import com.alananasss.kittytune.data.WaveformRepository
import com.alananasss.kittytune.data.local.NotificationExtraButton
import com.alananasss.kittytune.data.local.PlayerActionButtonSlot
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerDesign
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerProgressMode
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.data.local.TrackSourceBadgeStyle
import com.alananasss.kittytune.data.local.WaveformColorMode
import com.alananasss.kittytune.ui.common.AutoScrollToHighlightedItem
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.MenuTiles
import com.alananasss.kittytune.ui.player.slider.PlayerSlider
import com.alananasss.kittytune.ui.theme.ThemeState
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
    var backgroundStyle by remember { mutableStateOf(prefs.getPlayerStyle()) }
    var sourceBadgeStyle by remember { mutableStateOf(prefs.getTrackSourceBadgeStyle()) }
    var showBackgroundStyleDialog by remember { mutableStateOf(false) }
    var showSourceBadgeStyleDialog by remember { mutableStateOf(false) }
    var showRemainingTime by remember { mutableStateOf(prefs.getShowRemainingTime()) }

    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }
    var fullPlayerSourceIndicator by remember { mutableStateOf(prefs.getFullPlayerSourceIndicatorEnabled()) }

    var waveformColorMode by remember { mutableStateOf(prefs.getWaveformColorMode()) }
    var waveformCustomColor by remember { mutableIntStateOf(prefs.getWaveformCustomColor()) }
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

    AutoScrollToHighlightedItem(
        listState = listState,
        keyToIndex = mapOf(
            "player_design_page" to 0,
            "pref_slider_style" to 1,
            "pref_show_remaining_time" to 2,
            "notif_player_extra_button" to 5,
            "pref_player_style" to 4,
            "pref_track_source_badge" to 4,
            "pref_full_player_source" to 4,
        )
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
                                },
                                highlightKey = "pref_show_remaining_time"
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
                            title = stringResource(R.string.pref_player_style),
                            subtitle = stringResource(
                                when (backgroundStyle) {
                                    PlayerBackgroundStyle.THEME -> R.string.style_theme
                                    PlayerBackgroundStyle.GRADIENT -> R.string.style_gradient
                                    PlayerBackgroundStyle.BLUR -> R.string.style_blur
                                    PlayerBackgroundStyle.APPLE_MUSIC -> R.string.style_apple_music
                                }
                            ),
                            icon = Icons.Rounded.Style,
                            onClick = { showBackgroundStyleDialog = true },
                            highlightKey = "pref_player_style"
                        )
                    }
                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_track_source_badge_title),
                            subtitle = stringResource(sourceBadgeStyle.titleRes),
                            icon = Icons.Rounded.Badge,
                            onClick = { showSourceBadgeStyleDialog = true },
                            highlightKey = "pref_track_source_badge"
                        )
                    }

                    add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_full_player_source_title),
                            subtitle = stringResource(R.string.pref_full_player_source_desc),
                            icon = Icons.Rounded.GraphicEq,
                            hasSwitch = true,
                            switchState = fullPlayerSourceIndicator,
                            onSwitchChange = {
                                fullPlayerSourceIndicator = it
                                prefs.setFullPlayerSourceIndicatorEnabled(it)
                                onUpdated()
                            },
                            highlightKey = "pref_full_player_source"
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
                        onClick = { showNotifExtraButtonDialog = true },
                        highlightKey = "notif_player_extra_button"
                    )
                }

                SettingsGroup(
                    title = stringResource(R.string.notif_player_options_group),
                    items = notifItems
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

    if (showBackgroundStyleDialog) {
        val styles = PlayerBackgroundStyle.entries
        AlertDialog(
            onDismissRequest = { showBackgroundStyleDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_player_style),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    styles.forEachIndexed { idx, style ->
                        val isSelected = backgroundStyle == style
                        SettingsItem(
                            shape = getSettingsShape(styles.size, idx),
                            title = stringResource(
                                when (style) {
                                    PlayerBackgroundStyle.THEME -> R.string.style_theme
                                    PlayerBackgroundStyle.GRADIENT -> R.string.style_gradient
                                    PlayerBackgroundStyle.BLUR -> R.string.style_blur
                                    PlayerBackgroundStyle.APPLE_MUSIC -> R.string.style_apple_music
                                }
                            ),
                            trailingText = if (isSelected) stringResource(R.string.player_slot_active) else null,
                            onClick = {
                                backgroundStyle = style
                                prefs.setPlayerStyle(style)
                                showBackgroundStyleDialog = false
                                onUpdated()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackgroundStyleDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showSourceBadgeStyleDialog) {
        val badgeStyles = TrackSourceBadgeStyle.entries
        AlertDialog(
            onDismissRequest = { showSourceBadgeStyleDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_track_source_badge_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    badgeStyles.forEachIndexed { idx, style ->
                        val isSelected = sourceBadgeStyle == style
                        SettingsItem(
                            shape = getSettingsShape(badgeStyles.size, idx),
                            title = stringResource(style.titleRes),
                            trailingText = if (isSelected) stringResource(R.string.player_slot_active) else null,
                            onClick = {
                                sourceBadgeStyle = style
                                prefs.setTrackSourceBadgeStyle(style)
                                showSourceBadgeStyleDialog = false
                                onUpdated()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceBadgeStyleDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
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
@Composable
fun WaveformColorDialog(
    currentMode: WaveformColorMode,
    currentColor: Int,
    onModeSelected: (WaveformColorMode) -> Unit,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedMode by remember { mutableStateOf(currentMode) }
    var selectedColor by remember { mutableIntStateOf(currentColor) }

    val initialHsv = remember(currentColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentColor, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    var hexInput by remember {
        mutableStateOf(String.format("%06X", currentColor and 0xFFFFFF))
    }
    var hexError by remember { mutableStateOf(false) }

    fun updateColorFromHsv(newH: Float, newS: Float, newV: Float) {
        hue = newH
        saturation = newS
        brightness = newV
        val argb = AndroidColor.HSVToColor(floatArrayOf(newH, newS, newV))
        selectedColor = argb
        hexInput = String.format("%06X", argb and 0xFFFFFF)
        hexError = false
        onColorSelected(argb)
    }

    fun selectPreset(presetInt: Int) {
        selectedColor = presetInt
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(presetInt, it) }
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        hexInput = String.format("%06X", presetInt and 0xFFFFFF)
        hexError = false
        onColorSelected(presetInt)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val presetColors = remember {
        listOf(
            0xFFFF5500.toInt(), // SoundCloud Orange
            0xFFFF3366.toInt(), // Neon Coral Pink
            0xFFE53935.toInt(), // Crimson Red
            0xFF8E24AA.toInt(), // Purple
            0xFF3F51B5.toInt(), // Indigo
            0xFF1E88E5.toInt(), // Sky Blue
            0xFF00ACC1.toInt(), // Cyan
            0xFF00E676.toInt(), // Mint Green
            0xFF43A047.toInt(), // Emerald Green
            0xFFFFB300.toInt(), // Amber Gold
            0xFFFFFFFF.toInt()  // Pure White
        )
    }

    val hueGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF0000), // Red
                Color(0xFFFFFF00), // Yellow
                Color(0xFF00FF00), // Green
                Color(0xFF00FFFF), // Cyan
                Color(0xFF0000FF), // Blue
                Color(0xFFFF00FF), // Magenta
                Color(0xFFFF0000)  // Red
            )
        )
    }

    val satGradient = remember(hue, brightness) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0.0f, brightness))),
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1.0f, brightness)))
            )
        )
    }

    val brightGradient = remember(hue, saturation) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Black,
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1.0f)))
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.pref_waveform_color_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.pref_waveform_color_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Live Waveform Preview Card
                MiniWaveformPreview(
                    mode = selectedMode,
                    customColor = selectedColor
                )

                // 2. Mode Cards Grid (2 rows x 2 columns)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_soundcloud_title),
                            subtitle = stringResource(R.string.waveform_mode_soundcloud_sub),
                            isSelected = selectedMode == WaveformColorMode.SOUNDCLOUD,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5500))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.SOUNDCLOUD
                                onModeSelected(WaveformColorMode.SOUNDCLOUD)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_cover_title),
                            subtitle = stringResource(R.string.waveform_mode_cover_sub),
                            isSelected = selectedMode == WaveformColorMode.COVER_ART,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color(0xFFE53935),
                                                    Color(0xFFFFB300),
                                                    Color(0xFF43A047),
                                                    Color(0xFF1E88E5),
                                                    Color(0xFF8E24AA),
                                                    Color(0xFFE53935)
                                                )
                                            )
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.COVER_ART
                                onModeSelected(WaveformColorMode.COVER_ART)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_theme_title),
                            subtitle = stringResource(R.string.waveform_mode_theme_sub),
                            isSelected = selectedMode == WaveformColorMode.APP_THEME,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.APP_THEME
                                onModeSelected(WaveformColorMode.APP_THEME)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_custom_title),
                            subtitle = stringResource(R.string.waveform_mode_custom_sub),
                            isSelected = selectedMode == WaveformColorMode.CUSTOM,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(selectedColor))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.CUSTOM
                                onModeSelected(WaveformColorMode.CUSTOM)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Custom Color Section (animated)
                AnimatedVisibility(
                    visible = selectedMode == WaveformColorMode.CUSTOM,
                    enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // Expressive Color Sliders Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                WaveformColorSlider(
                                    label = stringResource(R.string.color_picker_hue),
                                    value = hue,
                                    valueText = "${hue.toInt()}°",
                                    valueRange = 0f..360f,
                                    gradientBrush = hueGradient,
                                    onValueChange = { newHue ->
                                        updateColorFromHsv(newHue, saturation, brightness)
                                    }
                                )

                                WaveformColorSlider(
                                    label = stringResource(R.string.color_picker_saturation),
                                    value = saturation,
                                    valueText = "${(saturation * 100).toInt()}%",
                                    valueRange = 0f..1f,
                                    gradientBrush = satGradient,
                                    onValueChange = { newSat ->
                                        updateColorFromHsv(hue, newSat, brightness)
                                    }
                                )

                                WaveformColorSlider(
                                    label = stringResource(R.string.color_picker_brightness),
                                    value = brightness,
                                    valueText = "${(brightness * 100).toInt()}%",
                                    valueRange = 0f..1f,
                                    gradientBrush = brightGradient,
                                    onValueChange = { newBright ->
                                        updateColorFromHsv(hue, saturation, newBright)
                                    }
                                )
                            }
                        }

                        // Presets Swatches
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.color_picker_presets),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(presetColors.size) { idx ->
                                    val colorInt = presetColors[idx]
                                    val isColorActive = (selectedColor and 0xFFFFFF) == (colorInt and 0xFFFFFF)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorInt))
                                            .border(
                                                width = if (isColorActive) 2.5.dp else 1.dp,
                                                color = if (isColorActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                            .clickable { selectPreset(colorInt) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isColorActive) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = if ((colorInt and 0xFFFFFF) == 0xFFFFFF || (colorInt and 0xFFFFFF) == 0xFFFFB300.toInt()) Color.Black else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // HEX input row + preview swatch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { input ->
                                    val filtered = input.take(6).filter { c ->
                                        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
                                    }
                                    hexInput = filtered.uppercase()
                                    if (filtered.length == 6) {
                                        try {
                                            val parsed = (0xFF000000L or filtered.toLong(16)).toInt()
                                            selectedColor = parsed
                                            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) }
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            brightness = hsv[2]
                                            hexError = false
                                            onColorSelected(parsed)
                                        } catch (e: Exception) {
                                            hexError = true
                                        }
                                    } else {
                                        hexError = false
                                    }
                                },
                                label = { Text("HEX") },
                                prefix = { Text("#") },
                                singleLine = true,
                                isError = hexError,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    keyboardType = KeyboardType.Ascii
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(selectedColor),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_ok),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun MiniWaveformPreview(
    mode: WaveformColorMode,
    customColor: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack = MusicManager.currentTrack
    val trackId = currentTrack?.id

    var waveformSamples by remember(trackId) {
        mutableStateOf(trackId?.let { WaveformRepository.getCachedWaveform(it) })
    }

    LaunchedEffect(trackId) {
        if (currentTrack != null && waveformSamples == null) {
            val samples = withContext(Dispatchers.IO) {
                WaveformRepository.getWaveform(context, currentTrack)
            }
            if (samples != null) {
                waveformSamples = samples
            }
        }
    }

    val fallbackBars = remember {
        val rng = java.util.Random(13L)
        FloatArray(200) { i ->
            val base = (Math.sin(i * 0.08) * 0.3 + 0.55).toFloat()
            val noise = (rng.nextFloat() - 0.5f) * 0.25f
            (base + noise).coerceIn(0.08f, 0.95f)
        }
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val targetAccentColor = remember(mode, customColor, themePrimary, ThemeState.coverSeedColor) {
        when (mode) {
            WaveformColorMode.SOUNDCLOUD -> Color(0xFFFF5500)
            WaveformColorMode.COVER_ART -> ThemeState.coverSeedColor?.let { Color(it) } ?: Color(0xFFE53935)
            WaveformColorMode.APP_THEME -> themePrimary
            WaveformColorMode.CUSTOM -> Color(customColor)
        }
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 300),
        label = "miniWaveformAccentColor"
    )
    val inactiveBarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    var progressFrac by remember { mutableFloatStateOf(0.55f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.waveform_preview_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentTrack != null && !currentTrack.title.isNullOrBlank()) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = when (mode) {
                            WaveformColorMode.SOUNDCLOUD -> "SoundCloud"
                            WaveformColorMode.COVER_ART -> "Auto Match"
                            WaveformColorMode.APP_THEME -> "App Theme"
                            WaveformColorMode.CUSTOM -> String.format("#%06X", customColor and 0xFFFFFF)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                val density = LocalDensity.current
                val canvasWidthPx = with(density) { maxWidth.toPx() }
                val barWidthPx = with(density) { 2.2.dp.toPx() }
                val gapPx = with(density) { 1.2.dp.toPx() }
                val stepPx = barWidthPx + gapPx
                val cornerRadius = CornerRadius(barWidthPx / 2f)

                val targetBarCount = (canvasWidthPx / stepPx).toInt().coerceAtLeast(20)

                val resampledBars = remember(waveformSamples, targetBarCount) {
                    val raw = waveformSamples ?: fallbackBars
                    val rawSize = raw.size
                    val result = FloatArray(targetBarCount)
                    for (j in 0 until targetBarCount) {
                        val startIdx = (j.toLong() * rawSize / targetBarCount).toInt()
                        val endIdx = (((j + 1).toLong() * rawSize / targetBarCount).toInt())
                            .coerceAtMost(rawSize)
                            .coerceAtLeast(startIdx + 1)
                        var sum = 0f
                        for (k in startIdx until endIdx) {
                            sum += raw[k]
                        }
                        result[j] = (sum / (endIdx - startIdx)).coerceIn(0.04f, 1f)
                    }
                    result
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(canvasWidthPx) {
                            detectTapGestures { offset ->
                                progressFrac = (offset.x / size.width).coerceIn(0.05f, 0.95f)
                            }
                        }
                        .pointerInput(canvasWidthPx) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                progressFrac = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                            }
                        }
                ) {
                    val cH = size.height
                    val baselineY = cH * 0.60f
                    val reflectGap = 1.5.dp.toPx()

                    val barsToDraw = resampledBars
                    val barCount = barsToDraw.size
                    val cutoffX = size.width * progressFrac

                    for (i in 0 until barCount) {
                        val x = i * stepPx
                        if (x + barWidthPx < 0f || x > size.width) continue

                        val h = barsToDraw[i]
                        val isPlayed = (x + barWidthPx / 2f) <= cutoffX

                        val topH = (baselineY * h * 0.92f).coerceAtLeast(3f)
                        val botH = ((cH - baselineY - reflectGap) * h * 0.65f).coerceAtLeast(2f)

                        val topColor = if (isPlayed) accentColor else inactiveBarColor
                        val botColor = if (isPlayed) accentColor.copy(alpha = 0.50f) else inactiveBarColor.copy(alpha = 0.30f)

                        drawRoundRect(
                            color = topColor,
                            topLeft = Offset(x, baselineY - topH),
                            size = Size(barWidthPx, topH),
                            cornerRadius = cornerRadius
                        )
                        drawRoundRect(
                            color = botColor,
                            topLeft = Offset(x, baselineY + reflectGap),
                            size = Size(barWidthPx, botH),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformModeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    leadingContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingContent()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformColorSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientBrush: Brush,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(gradientBrush)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
            )

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


