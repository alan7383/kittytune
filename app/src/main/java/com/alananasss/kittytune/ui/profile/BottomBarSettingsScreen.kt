package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
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
import androidx.compose.material.icons.rounded.DragHandle
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.navigation.KittyTab
import com.alananasss.kittytune.ui.navigation.KittyUnifiedBottomBar
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

    var showStyleDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    val listState = rememberLazyListState()
    val availableTabs = listOf("home", "search", "genres", "library")

    val previewTabs = availableTabs.mapNotNull { key ->
        val screen = when (key) {
            "home" -> Screen.Home
            "search" -> Screen.Search
            "genres" -> Screen.Explore
            "library" -> Screen.Library
            else -> null
        } ?: return@mapNotNull null
        KittyTab(
            title = stringResource(screen.titleResId),
            icon = screen.icon ?: Icons.Rounded.Home,
            route = screen.route,
            visible = items.contains(key)
        )
    }

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

    val ordered = remember(items) { items.toMutableList() }
    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = ordered.indexOf(fromKey)
            val toIndex = ordered.indexOf(toKey)
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                val moved = ordered.removeAt(fromIndex)
                ordered.add(toIndex, moved)
                prefs.setBottomMenuItems(ordered)
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            }
        }
    )

    SettingsScaffold(
        title = stringResource(R.string.pref_bottom_menu_title),
        onBackClick = onBackClick
    ) { padding ->
        val miniPlayerHeight = if (playerViewModel.currentTrack != null) 64.dp else 0.dp
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
                        val totalItems = if (style == "modern") 3 else 1

                        SettingsItem(
                            shape = getSettingsShape(totalItems, 0),
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
                                    shape = getSettingsShape(3, 1),
                                    title = stringResource(R.string.pref_bottom_menu_fab),
                                    subtitle = currentSubtitle,
                                    onClick = onNavigateToFabSettings
                                )

                                SettingsItem(
                                    shape = getSettingsShape(3, 2),
                                    title = stringResource(R.string.pref_bottom_menu_blur),
                                    subtitle = stringResource(R.string.pref_bottom_menu_blur_sub),
                                    hasSwitch = true,
                                    switchState = blur,
                                    onSwitchChange = { prefs.setBottomMenuBlurEnabled(it) }
                                )
                            }
                        }
                    }
                }
            }
            item(key = "tabs_header") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_bottom_menu_tabs))
                    Text(
                        text = stringResource(R.string.pref_bottom_menu_tabs_reorder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            itemsIndexed(ordered, key = { _, key -> key }) { index, tabKey ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)) {
                    ReorderableItem(state = reorderState, key = tabKey) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 6.dp else 0.dp,
                            label = "tabElevation"
                        )
                        val isChecked = items.contains(tabKey)
                        SettingsItem(
                            modifier = Modifier
                                .shadow(elevation, RoundedCornerShape(0.dp))
                                .zIndex(if (isDragging) 1f else 0f),
                            shape = getSettingsShape(ordered.size, index),
                            title = when (tabKey) {
                                "home" -> stringResource(R.string.nav_home)
                                "search" -> stringResource(R.string.nav_search)
                                "genres" -> stringResource(R.string.explorer_title)
                                "library" -> stringResource(R.string.nav_library)
                                else -> tabKey
                            },
                            hasSwitch = true,
                            switchState = isChecked,
                            onSwitchChange = { checked ->
                                val newItems = items.toMutableList()
                                if (checked) {
                                    // Appended at the end, so turning one back on does not
                                    // silently reshuffle the rest.
                                    if (!newItems.contains(tabKey)) newItems.add(tabKey)
                                } else {
                                    if (newItems.size > 1) newItems.remove(tabKey)
                                }
                                prefs.setBottomMenuItems(newItems)
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = stringResource(R.string.reorder_handle),
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .draggableHandle(
                                            onDragStarted = {
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.LONG_PRESS
                                                )
                                            },
                                            onDragStopped = {
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.GESTURE_END
                                                )
                                            }
                                        )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
