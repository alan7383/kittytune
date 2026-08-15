package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

    SettingsScaffold(
        title = stringResource(R.string.pref_bottom_menu_title),
        onBackClick = onBackClick
    ) { padding ->
        val miniPlayerHeight = if (playerViewModel.currentTrack != null) 64.dp else 0.dp
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = padding.calculateBottomPadding() + miniPlayerHeight + 150.dp,
                top = 8.dp
            )
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_general))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_bottom_menu_tabs))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        availableTabs.forEachIndexed { index, tabKey ->
                            val isChecked = items.contains(tabKey)
                            SettingsItem(
                                shape = getSettingsShape(availableTabs.size, index),
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
                                        if (!newItems.contains(tabKey)) newItems.add(tabKey)
                                    } else {
                                        // Ensure at least one item remains
                                        if (newItems.size > 1) {
                                            newItems.remove(tabKey)
                                        }
                                    }
                                    // Preserve original order based on availableTabs
                                    val orderedNewItems = availableTabs.filter { it in newItems }
                                    prefs.setBottomMenuItems(orderedNewItems)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
