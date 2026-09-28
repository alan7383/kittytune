package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

    val view = LocalView.current
    val listState = rememberLazyListState()

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
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
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
                            else -> null
                        }

                        val icon = when (tabKey) {
                            "home" -> Icons.Rounded.Home
                            "search" -> Icons.Rounded.Search
                            "genres" -> Icons.Default.Explore
                            "library" -> Icons.Default.LibraryMusic
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
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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
