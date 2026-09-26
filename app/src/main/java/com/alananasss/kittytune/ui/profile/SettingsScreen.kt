package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * The settings, as the desktop arranges them: the same seven categories, in the same order, with
 * the same rows inside each.
 *
 * The desktop shows the categories down the left and swaps the right pane, with a back arrow that
 * walks out of a sub-page before it leaves settings. A phone has no room for two panes, so the
 * category list is the screen and a row pushes its page - the navigation back stack is the same
 * history the desktop keeps in [SettingsNavigation], and the in-page back arrow appears on the
 * pages that have one.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp)
        ) {
            SettingsCategory.entries.forEach { category ->
                item(key = "cat-${category.name}") {
                    SettingsGroup(
                        title = stringResource(category.titleRes),
                        items = category.entriesFor().map { entry ->
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(entry.titleRes),
                                    subtitle = entry.subtitleRes?.let { stringResource(it) },
                                    icon = entry.icon,
                                    onClick = { navController.navigate(entry.route) }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * The Interface category's sub-pages.
 *
 * A screen rather than a group, because the desktop keeps the category list on the left and swaps
 * only the right pane; on a phone the equivalent is a pushed page with a back arrow, so the
 * category list is still one back away.
 */
@Composable
fun InterfaceSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_cat_interface),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp)
        ) {
            item {
                SettingsGroup(
                    items = SettingsSubPage.interfacePages.map { page ->
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(page.titleRes),
                                subtitle = page.subtitleRes?.let { stringResource(it) },
                                icon = page.icon,
                                onClick = { navController.navigate(page.route) }
                            )
                        }
                    }
                )
            }
        }
    }
}
