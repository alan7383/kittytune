package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape

/**
 * The MISC category: everything that is not a colour, a sound, a source or a device.
 *
 * Language, start screen and auto-update live here, as on the desktop. They were in the Appearance
 * screen, where none of them belongs: none of them changes how anything looks, and hiding them
 * there is what made that screen long enough to lose people in.
 */
@Composable
fun MiscSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }
    var aiBlockAll by remember { mutableStateOf(prefs.getAiBlockAllEnabled()) }
    var showAiBlockWarning by remember { mutableStateOf(false) }

    // Switching the block on skips music without asking, so the listener reads what can go wrong first.
    if (showAiBlockWarning) {
        AlertDialog(
            onDismissRequest = { showAiBlockWarning = false },
            icon = { Icon(Icons.Rounded.Block, contentDescription = null) },
            title = { Text(stringResource(R.string.ai_block_all_warning_title)) },
            text = {
                Text(
                    text = stringResource(R.string.ai_block_all_warning_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    aiBlockAll = true
                    prefs.setAiBlockAllEnabled(true)
                    showAiBlockWarning = false
                }) {
                    Text(stringResource(R.string.ai_block_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiBlockWarning = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

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

    SettingsScaffold(
        title = stringResource(R.string.settings_cat_misc),
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
                                onClick = { showLanguageDialog = true }
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
                                onClick = { showStartDialog = true }
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
                                }
                            )
                        }
                    )
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_discord_title))
                    SettingsItem(
                        shape = getSettingsShape(1, 0),
                        title = stringResource(R.string.pref_discord_title),
                        subtitle = stringResource(R.string.pref_discord_subtitle),
                        icon = Icons.Rounded.Forum,
                        onClick = { navController.navigate("discord_settings") }
                    )
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.ai_settings_group),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.ai_block_all_title),
                                subtitle = stringResource(R.string.ai_block_all_subtitle),
                                icon = Icons.Rounded.Block,
                                hasSwitch = true,
                                switchState = aiBlockAll,
                                onSwitchChange = { enabled ->
                                    if (enabled) {
                                        showAiBlockWarning = true
                                    } else {
                                        aiBlockAll = false
                                        prefs.setAiBlockAllEnabled(false)
                                    }
                                }
                            )
                        }
                    )
                )
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_playback),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_haptics_title),
                                subtitle = stringResource(R.string.pref_haptics_subtitle),
                                icon = Icons.Rounded.Vibration,
                                onClick = { navController.navigate("haptic_settings") }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.music_import_title),
                                subtitle = stringResource(R.string.music_import_settings_subtitle),
                                icon = Icons.Rounded.ImportExport,
                                onClick = { navController.navigate("music_import") }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_about_title),
                                subtitle = stringResource(R.string.pref_about_subtitle),
                                icon = Icons.Rounded.Info,
                                onClick = { navController.navigate("about") }
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
