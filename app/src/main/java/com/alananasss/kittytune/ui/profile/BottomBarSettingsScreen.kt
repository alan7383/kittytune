package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.audio.haptics.performAppHaptic
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.AutoScrollToHighlightedItem
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.getSettingsShape
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import com.alananasss.kittytune.ui.navigation.Screen
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun BottomBarSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToFabSettings: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    val style by prefs.bottomMenuStyleFlow().collectAsState(initial = prefs.getBottomMenuStyle())
    val blur by prefs.bottomMenuBlurFlow().collectAsState(initial = prefs.getBottomMenuBlurEnabled())
    val items by prefs.bottomMenuItemsFlow().collectAsState(initial = prefs.getBottomMenuItems())
    val fab by prefs.bottomMenuFabFlow().collectAsState(initial = prefs.getBottomMenuFab())
    val storedOrder by prefs.bottomMenuOrderFlow().collectAsState(initial = prefs.getBottomMenuOrder())

    var showStyleDialog by remember { mutableStateOf(false) }
    val miniPlayerSwipeAction by prefs.miniPlayerSwipeActionFlow().collectAsState(initial = prefs.getMiniPlayerSwipeAction())
    var showSwipeActionDialog by remember { mutableStateOf(false) }

    // ── Navigation: one labels switch for both styles + placement on tablets ──
    val tabletNavPlacement by prefs.tabletNavPlacementFlow().collectAsState(initial = prefs.getTabletNavPlacement())
    var showTabletNavDialog by remember { mutableStateOf(false) }
    val navHideLabels by prefs.navHideLabelsFlow().collectAsState(initial = prefs.getNavHideLabels())

    // ── Mini player (the whole section moved here from the player design screen) ──
    val miniPlayerStyle by prefs.miniPlayerStyleFlow().collectAsState(initial = prefs.getMiniPlayerStyle())
    val miniPlayerBackground by prefs.miniPlayerBackgroundStyleFlow().collectAsState(initial = prefs.getMiniPlayerBackgroundStyle())
    var showMiniPlayerBackgroundDialog by remember { mutableStateOf(false) }
    val miniPlayerSwipeTrack by prefs.miniPlayerSwipeTrackFlow().collectAsState(initial = prefs.getMiniPlayerSwipeTrack())
    val miniPlayerVerticalGesture by prefs.miniPlayerVerticalGestureFlow().collectAsState(initial = prefs.getMiniPlayerVerticalGesture())
    val miniPlayerShowTrackButtons by prefs.miniPlayerShowTrackButtonsFlow().collectAsState(initial = prefs.getMiniPlayerShowTrackButtons())
    val miniPlayerWidth by prefs.miniPlayerWidthFlow().collectAsState(initial = prefs.getMiniPlayerWidth())
    var showMiniPlayerWidthDialog by remember { mutableStateOf(false) }
    var showMiniPlayerStyleDialog by remember { mutableStateOf(false) }
    val wavyPlayPause by prefs.wavyPlayPauseFlow().collectAsState(initial = prefs.getWavyPlayPauseButton())

    // The placement row only makes sense where a left rail can actually live.
    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()

    val view = LocalView.current
    val listState = rememberLazyListState()

    AutoScrollToHighlightedItem(
        listState = listState,
        keyToIndex = mapOf(
            "mini_player_swipe_action" to 2,
            "pref_tablet_nav_placement" to 0,
            "pref_nav_hide_labels" to 0,
            "pref_mini_player_style" to 1,
            "pref_mini_player_background" to 1,
            "pref_mini_player_swipe_track" to 1,
            "pref_mini_player_vertical_gesture" to 1,
            "pref_mini_player_show_track_buttons" to 1,
            "pref_mini_player_width" to 1,
            "pref_wavy_play_pause" to 1
        )
    )

    val currentOrder = remember { mutableStateListOf<String>() }
    LaunchedEffect(storedOrder) {
        if (currentOrder.isEmpty() || currentOrder.toSet() != storedOrder.toSet()) {
            currentOrder.clear()
            currentOrder.addAll(storedOrder)
        }
    }

    fun persistOrder() {
        val list = currentOrder.toList()
        prefs.setBottomMenuOrder(list)
        prefs.setBottomMenuItems(list.filter { it in items })
    }

    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = currentOrder.indexOf(fromKey)
            val toIndex = currentOrder.indexOf(toKey)
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                val moved = currentOrder.removeAt(fromIndex)
                currentOrder.add(toIndex, moved)
                persistOrder()
                view.performAppHaptic(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            }
        }
    )

    if (showStyleDialog) {
        AlertDialog(
            onDismissRequest = { showStyleDialog = false },
            title = { Text(stringResource(R.string.pref_bottom_menu_style)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { prefs.setBottomMenuStyle("modern"); showStyleDialog = false }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = style == "modern", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pref_bottom_menu_style_modern))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { prefs.setBottomMenuStyle("classic"); showStyleDialog = false }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = style == "classic", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pref_bottom_menu_style_classic))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStyleDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showTabletNavDialog) {
        AlertDialog(
            onDismissRequest = { showTabletNavDialog = false },
            title = { Text(stringResource(R.string.pref_tablet_nav_placement), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        com.alananasss.kittytune.data.local.TabletNavPlacement.BOTTOM to R.string.pref_tablet_nav_bottom,
                        com.alananasss.kittytune.data.local.TabletNavPlacement.LEFT to R.string.pref_tablet_nav_left,
                    ).forEach { (option, labelRes) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.setTabletNavPlacement(option)
                                    showTabletNavDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = tabletNavPlacement == option,
                                onClick = {
                                    prefs.setTabletNavPlacement(option)
                                    showTabletNavDialog = false
                                },
                            )
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTabletNavDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showSwipeActionDialog) {
        AlertDialog(
            onDismissRequest = { showSwipeActionDialog = false },
            title = { Text(stringResource(R.string.pref_mini_player_swipe_action_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                prefs.setMiniPlayerSwipeAction(com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.CHANGE_TRACK)
                                showSwipeActionDialog = false
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
                                showSwipeActionDialog = false
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
                TextButton(onClick = { showSwipeActionDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showMiniPlayerBackgroundDialog) {
        AlertDialog(
            onDismissRequest = { showMiniPlayerBackgroundDialog = false },
            title = { Text(stringResource(R.string.pref_mini_player_background), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.SOLID to R.string.pref_mini_player_bg_solid,
                        com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.GRADIENT to R.string.pref_mini_player_bg_gradient,
                        com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.BLUR to R.string.pref_mini_player_bg_blur,
                        com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.GLOW to R.string.pref_mini_player_bg_glow,
                        com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.LIVE_MESH to R.string.pref_mini_player_bg_live_mesh
                    ).forEach { (option, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    prefs.setMiniPlayerBackgroundStyle(option)
                                    showMiniPlayerBackgroundDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = miniPlayerBackground == option, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMiniPlayerBackgroundDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showMiniPlayerWidthDialog) {
        AlertDialog(
            onDismissRequest = { showMiniPlayerWidthDialog = false },
            title = { Text(stringResource(R.string.pref_mini_player_width), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                prefs.setMiniPlayerWidth(com.alananasss.kittytune.data.local.MiniPlayerWidth.WIDE)
                                showMiniPlayerWidthDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = miniPlayerWidth == com.alananasss.kittytune.data.local.MiniPlayerWidth.WIDE,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.pref_mini_player_width_wide),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.pref_mini_player_width_wide_desc),
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
                                prefs.setMiniPlayerWidth(com.alananasss.kittytune.data.local.MiniPlayerWidth.NARROW)
                                showMiniPlayerWidthDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = miniPlayerWidth == com.alananasss.kittytune.data.local.MiniPlayerWidth.NARROW,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.pref_mini_player_width_narrow),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.pref_mini_player_width_narrow_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMiniPlayerWidthDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showMiniPlayerStyleDialog) {
        AlertDialog(
            onDismissRequest = { showMiniPlayerStyleDialog = false },
            title = { Text(stringResource(R.string.pref_mini_player_style), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                prefs.setMiniPlayerStyle(com.alananasss.kittytune.data.local.MiniPlayerStyle.CLASSIC)
                                showMiniPlayerStyleDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = miniPlayerStyle == com.alananasss.kittytune.data.local.MiniPlayerStyle.CLASSIC,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.pref_mini_player_style_classic),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                prefs.setMiniPlayerStyle(com.alananasss.kittytune.data.local.MiniPlayerStyle.MODERN)
                                showMiniPlayerStyleDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = miniPlayerStyle == com.alananasss.kittytune.data.local.MiniPlayerStyle.MODERN,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.pref_mini_player_style_modern),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMiniPlayerStyleDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_bottom_menu_title),
        onBackClick = onBackClick
    ) { padding ->
        val miniPlayerHeight = if (playerViewModel.uiCurrentTrack != null) 64.dp else 0.dp
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = padding.calculateBottomPadding() + miniPlayerHeight + 150.dp,
                top = 8.dp
            )
        ) {
            item(key = "general_section") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_general))
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // One merged card: menu style, FAB, blur, then the navigation
                        // rows (tablet placement + hide-labels) appended below.
                        val modern = style == "modern"
                        val tablet = windowSizeInfo.isTablet
                        val hideLabelsIndex = (if (modern) 2 else 0) + (if (tablet) 2 else 1)
                        val groupSize = hideLabelsIndex + 1

                        SettingsItem(
                            shape = getSettingsShape(groupSize, 0),
                            title = stringResource(R.string.pref_bottom_menu_style),
                            subtitle = if (style == "modern") stringResource(R.string.pref_bottom_menu_style_modern) else stringResource(R.string.pref_bottom_menu_style_classic),
                            onClick = { showStyleDialog = true }
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = style == "modern",
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val currentSubtitle = when {
                                    fab == "settings" -> stringResource(R.string.pref_bottom_menu_fab_settings)
                                    fab == "recognition" -> stringResource(R.string.pref_bottom_menu_fab_recognition)
                                    fab == "achievements" -> stringResource(R.string.achievements_title)
                                    fab == "stats" -> stringResource(R.string.pref_bottom_menu_fab_stats)
                                    fab == "liked" -> stringResource(R.string.lib_liked_tracks)
                                    fab == "downloads" -> stringResource(R.string.lib_downloads)
                                    fab == "local" -> stringResource(R.string.lib_local_media)
                                    fab.startsWith("playlist:") -> stringResource(R.string.pref_bottom_menu_fab_playlist)
                                    else -> stringResource(R.string.pref_bottom_menu_fab_profile)
                                }
                                SettingsItem(
                                    shape = getSettingsShape(groupSize, 1),
                                    title = stringResource(R.string.pref_bottom_menu_fab),
                                    subtitle = currentSubtitle,
                                    onClick = onNavigateToFabSettings
                                )

                                SettingsItem(
                                    shape = getSettingsShape(groupSize, 2),
                                    title = stringResource(R.string.pref_bottom_menu_blur),
                                    subtitle = stringResource(R.string.pref_bottom_menu_blur_sub),
                                    hasSwitch = true,
                                    switchState = blur,
                                    onSwitchChange = { prefs.setBottomMenuBlurEnabled(it) }
                                )
                            }
                        }

                        // Hidden on phones: a left rail can only exist on a tablet.
                        if (windowSizeInfo.isTablet) {
                            SettingsItem(
                                shape = getSettingsShape(groupSize, hideLabelsIndex - 1),
                                title = stringResource(R.string.pref_tablet_nav_placement),
                                subtitle = stringResource(R.string.pref_tablet_nav_placement_sub),
                                trailingText = when (tabletNavPlacement) {
                                    com.alananasss.kittytune.data.local.TabletNavPlacement.LEFT -> stringResource(R.string.pref_tablet_nav_left)
                                    else -> stringResource(R.string.pref_tablet_nav_bottom)
                                },
                                onClick = { showTabletNavDialog = true },
                                highlightKey = "pref_tablet_nav_placement"
                            )
                        }

                        SettingsItem(
                            shape = getSettingsShape(groupSize, hideLabelsIndex),
                            title = stringResource(R.string.pref_nav_hide_labels),
                            subtitle = stringResource(R.string.pref_nav_hide_labels_sub),
                            hasSwitch = true,
                            switchState = navHideLabels,
                            onSwitchChange = { prefs.setNavHideLabels(it) },
                            highlightKey = "pref_nav_hide_labels"
                        )
                    }
                }
            }
            item(key = "mini_player_section") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_mini_player_style))
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // The width switch only exists where a WIDE pill makes sense - on a
                        // tablet. Phones always run the compact pill, so the row is hidden there.
                        val modernGroupCount = if (windowSizeInfo.isTablet) 7 else 6
                        SettingsItem(
                            shape = if (miniPlayerStyle == com.alananasss.kittytune.data.local.MiniPlayerStyle.MODERN) getSettingsShape(modernGroupCount, 0) else getSettingsShape(2, 0),
                            title = stringResource(R.string.pref_mini_player_style),
                            subtitle = if (miniPlayerStyle == com.alananasss.kittytune.data.local.MiniPlayerStyle.MODERN) {
                                stringResource(R.string.pref_mini_player_style_modern)
                            } else {
                                stringResource(R.string.pref_mini_player_style_classic)
                            },
                            onClick = { showMiniPlayerStyleDialog = true },
                            highlightKey = "pref_mini_player_style"
                        )

                        if (miniPlayerStyle == com.alananasss.kittytune.data.local.MiniPlayerStyle.MODERN) {
                            // ── Modern (Mini) pill ──
                            SettingsItem(
                                shape = getSettingsShape(modernGroupCount, 1),
                                title = stringResource(R.string.pref_mini_player_swipe_track),
                                subtitle = stringResource(R.string.pref_mini_player_swipe_track_desc),
                                hasSwitch = true,
                                switchState = miniPlayerSwipeTrack,
                                onSwitchChange = { prefs.setMiniPlayerSwipeTrack(it) },
                                highlightKey = "pref_mini_player_swipe_track"
                            )
                            SettingsItem(
                                shape = getSettingsShape(modernGroupCount, 2),
                                title = stringResource(R.string.pref_mini_player_vertical_gesture),
                                subtitle = stringResource(R.string.pref_mini_player_vertical_gesture_sub),
                                hasSwitch = true,
                                switchState = miniPlayerVerticalGesture == com.alananasss.kittytune.data.local.MiniPlayerVerticalGesture.CLOSE,
                                onSwitchChange = { on ->
                                    prefs.setMiniPlayerVerticalGesture(
                                        if (on) com.alananasss.kittytune.data.local.MiniPlayerVerticalGesture.CLOSE
                                        else com.alananasss.kittytune.data.local.MiniPlayerVerticalGesture.OFF
                                    )
                                },
                                highlightKey = "pref_mini_player_vertical_gesture"
                            )
                            SettingsItem(
                                shape = getSettingsShape(modernGroupCount, 3),
                                title = stringResource(R.string.pref_mini_player_show_track_buttons),
                                subtitle = stringResource(R.string.pref_mini_player_show_track_buttons_desc),
                                hasSwitch = true,
                                switchState = miniPlayerShowTrackButtons,
                                onSwitchChange = { prefs.setMiniPlayerShowTrackButtons(it) },
                                highlightKey = "pref_mini_player_show_track_buttons"
                            )
                            if (windowSizeInfo.isTablet) {
                                SettingsItem(
                                    shape = getSettingsShape(modernGroupCount, 4),
                                    title = stringResource(R.string.pref_mini_player_width),
                                    subtitle = when (miniPlayerWidth) {
                                        com.alananasss.kittytune.data.local.MiniPlayerWidth.NARROW -> stringResource(R.string.pref_mini_player_width_narrow)
                                        else -> stringResource(R.string.pref_mini_player_width_wide)
                                    },
                                    onClick = { showMiniPlayerWidthDialog = true },
                                    highlightKey = "pref_mini_player_width"
                                )
                            }
                            SettingsItem(
                                shape = getSettingsShape(modernGroupCount, if (windowSizeInfo.isTablet) 5 else 4),
                                title = stringResource(R.string.pref_mini_player_background),
                                subtitle = when (miniPlayerBackground) {
                                    com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.GRADIENT -> stringResource(R.string.pref_mini_player_bg_gradient)
                                    com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.BLUR -> stringResource(R.string.pref_mini_player_bg_blur)
                                    com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.GLOW -> stringResource(R.string.pref_mini_player_bg_glow)
                                    com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle.LIVE_MESH -> stringResource(R.string.pref_mini_player_bg_live_mesh)
                                    else -> stringResource(R.string.pref_mini_player_bg_solid)
                                },
                                onClick = { showMiniPlayerBackgroundDialog = true },
                                highlightKey = "pref_mini_player_background"
                            )
                            SettingsItem(
                                shape = getSettingsShape(modernGroupCount, if (windowSizeInfo.isTablet) 6 else 5),
                                title = stringResource(R.string.pref_wavy_play_pause),
                                subtitle = stringResource(R.string.pref_wavy_play_pause_sub),
                                hasSwitch = true,
                                switchState = wavyPlayPause,
                                onSwitchChange = { prefs.setWavyPlayPauseButton(it) },
                                highlightKey = "pref_wavy_play_pause"
                            )
                        } else {
                            // ── Classic strip: just the swipe gesture (2 options) ──
                            SettingsItem(
                                shape = getSettingsShape(2, 1),
                                title = stringResource(R.string.pref_mini_player_swipe_action_title),
                                subtitle = when (miniPlayerSwipeAction) {
                                    com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.CHANGE_TRACK ->
                                        stringResource(R.string.pref_mini_player_swipe_action_change_track)
                                    com.alananasss.kittytune.data.local.MiniPlayerSwipeAction.DISMISS ->
                                        stringResource(R.string.pref_mini_player_swipe_action_dismiss)
                                },
                                onClick = { showSwipeActionDialog = true },
                                highlightKey = "mini_player_swipe_action"
                            )
                        }
                    }
                }
            }
            item(key = "tabs_header") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_bottom_menu_tabs))
                    Text(
                        text = stringResource(R.string.pref_bottom_menu_tabs_reorder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }
            itemsIndexed(currentOrder, key = { _, key -> key }) { index, tabKey ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)) {
                    ReorderableItem(state = reorderState, key = tabKey) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 8.dp else 0.dp,
                            label = "tabElevation"
                        )
                        val isChecked = items.contains(tabKey)
                        val isLastOn = isChecked && items.size == 1

                        val screen = when (tabKey) {
                            "home" -> Screen.Home
                            "search" -> Screen.Search
                            "genres" -> Screen.Explore
                            "library" -> Screen.Library
                            "queue" -> Screen.Queue
                            else -> null
                        }

                        val icon = when (tabKey) {
                            "home" -> Icons.Rounded.Home
                            "search" -> Icons.Rounded.Search
                            "genres" -> Icons.Default.Explore
                            "library" -> Icons.Default.LibraryMusic
                            "queue" -> Icons.AutoMirrored.Rounded.QueueMusic
                            else -> Icons.Rounded.Home
                        }

                        val title = screen?.let { stringResource(it.titleResId) } ?: tabKey

                        Surface(
                            shape = if (isDragging) RoundedCornerShape(16.dp) else getSettingsShape(currentOrder.size, index),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                                            },
                                            onDragStopped = {
                                                view.performAppHaptic(HapticFeedbackConstants.GESTURE_END)
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
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    modifier = Modifier.size(22.dp)
                                )

                                Spacer(Modifier.width(16.dp))

                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                SettingsSwitch(
                                    checked = isChecked,
                                    enabled = !isLastOn,
                                    onCheckedChange = if (isLastOn) null else { checked ->
                                        val newItems = if (checked) {
                                            currentOrder.filter { it in items || it == tabKey }
                                        } else {
                                            currentOrder.filter { it in items && it != tabKey }
                                        }
                                        prefs.setBottomMenuItems(newItems)
                                        view.performAppHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
