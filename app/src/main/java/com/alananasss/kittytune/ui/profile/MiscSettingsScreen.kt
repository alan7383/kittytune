package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.StartDestination
import androidx.compose.foundation.lazy.rememberLazyListState
import com.alananasss.kittytune.ui.common.AutoScrollToHighlightedItem
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

/**
 * The Miscellaneous category folder hub:
 * General, Content filter, Discord RPC, About.
 */
@Composable
fun MiscSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    SettingsFolderScreen(
        title = stringResource(R.string.settings_cat_misc),
        pages = SettingsSubPage.miscPages,
        navController = navController,
        onBackClick = onBackClick
    )
}

/**
 * The General subpage:
 * Language, start screen, updates, remember search filter, playlist total duration.
 */
@Composable
fun MiscGeneralSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var rememberSearchFilter by remember { mutableStateOf(prefs.getRememberSearchFilter()) }
    var showPlaylistTotalDuration by remember { mutableStateOf(prefs.getShowPlaylistTotalDuration()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.pref_language)) },
            text = {
                Column {
                    val onSelected: (AppLanguage) -> Unit = { selected ->
                        appLanguage = selected
                        prefs.setAppLanguage(selected)
                        showLanguageDialog = false
                        restartApp(context)
                    }
                    LanguageRadioButton(stringResource(R.string.theme_system), AppLanguage.SYSTEM, appLanguage, onSelected)
                    LanguageRadioButton(stringResource(R.string.lang_french), AppLanguage.FRENCH, appLanguage, onSelected)
                    LanguageRadioButton(stringResource(R.string.lang_english), AppLanguage.ENGLISH, appLanguage, onSelected)
                    LanguageRadioButton(stringResource(R.string.lang_german), AppLanguage.GERMAN, appLanguage, onSelected)
                    LanguageRadioButton(stringResource(R.string.lang_russian), AppLanguage.RUSSIAN, appLanguage, onSelected)
                    LanguageRadioButton(stringResource(R.string.lang_hungarian), AppLanguage.HUNGARIAN, appLanguage, onSelected)
                    LanguageRadioButton(stringResource(R.string.lang_vietnamese), AppLanguage.VIETNAMESE, appLanguage, onSelected)
                }
            },
            confirmButton = {}
        )
    }

    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = { Text(stringResource(R.string.pref_start_screen)) },
            text = {
                Column {
                    val onSelected: (StartDestination) -> Unit = { selected ->
                        startDestination = selected
                        prefs.setStartDestination(selected)
                        showStartDialog = false
                    }
                    StartDestRadioButton(stringResource(R.string.nav_home), StartDestination.HOME, startDestination, onSelected)
                    StartDestRadioButton(stringResource(R.string.nav_library), StartDestination.LIBRARY, startDestination, onSelected)
                }
            },
            confirmButton = {}
        )
    }

    val listState = rememberLazyListState()

    AutoScrollToHighlightedItem(
        listState = listState,
        keyToIndex = mapOf(
            "pref_language" to 0,
            "pref_start_screen" to 0,
            "pref_auto_update" to 0,
            "pref_remember_search_filter" to 0,
            "pref_show_playlist_total_duration" to 0
        )
    )

    SettingsScaffold(
        title = stringResource(R.string.settings_cat_general),
        subtitle = stringResource(R.string.misc_general_sub),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp)
        ) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_language),
                                subtitle = when (appLanguage) {
                                    AppLanguage.SYSTEM -> stringResource(R.string.theme_system)
                                    AppLanguage.FRENCH -> stringResource(R.string.lang_french)
                                    AppLanguage.ENGLISH -> stringResource(R.string.lang_english)
                                    AppLanguage.GERMAN -> stringResource(R.string.lang_german)
                                    AppLanguage.HUNGARIAN -> stringResource(R.string.lang_hungarian)
                                    AppLanguage.RUSSIAN -> stringResource(R.string.lang_russian)
                                    AppLanguage.VIETNAMESE -> stringResource(R.string.lang_vietnamese)
                                },
                                icon = Icons.Rounded.Translate,
                                onClick = { showLanguageDialog = true },
                                highlightKey = "pref_language"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_start_screen),
                                subtitle = if (startDestination == StartDestination.HOME) {
                                    stringResource(R.string.nav_home)
                                } else {
                                    stringResource(R.string.nav_library)
                                },
                                icon = Icons.Rounded.Home,
                                onClick = { showStartDialog = true },
                                highlightKey = "pref_start_screen"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_auto_update),
                                subtitle = stringResource(R.string.pref_auto_update_sub),
                                icon = Icons.Rounded.SystemUpdate,
                                hasSwitch = true,
                                switchState = autoUpdate,
                                onSwitchChange = {
                                    autoUpdate = it
                                    prefs.setAutoUpdateEnabled(it)
                                },
                                highlightKey = "pref_auto_update"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_remember_search_filter),
                                subtitle = stringResource(R.string.pref_remember_search_filter_sub),
                                icon = Icons.Rounded.FilterList,
                                hasSwitch = true,
                                switchState = rememberSearchFilter,
                                onSwitchChange = {
                                    rememberSearchFilter = it
                                    prefs.setRememberSearchFilter(it)
                                },
                                highlightKey = "pref_remember_search_filter"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_show_playlist_total_duration),
                                subtitle = stringResource(R.string.pref_show_playlist_total_duration_sub),
                                icon = Icons.Rounded.Schedule,
                                hasSwitch = true,
                                switchState = showPlaylistTotalDuration,
                                onSwitchChange = {
                                    showPlaylistTotalDuration = it
                                    prefs.setShowPlaylistTotalDuration(it)
                                },
                                highlightKey = "pref_show_playlist_total_duration"
                            )
                        }
                    )
                )
            }
        }
    }
}

@Composable
internal fun LanguageRadioButton(
    text: String,
    lang: AppLanguage,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(lang) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (lang == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
internal fun StartDestRadioButton(
    text: String,
    dest: StartDestination,
    selected: StartDestination,
    onSelect: (StartDestination) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(dest) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (dest == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
