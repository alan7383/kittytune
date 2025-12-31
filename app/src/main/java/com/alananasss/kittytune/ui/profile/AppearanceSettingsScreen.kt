package com.alananasss.kittytune.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    // grab player vm to know if we need padding
    val playerViewModel: PlayerViewModel = viewModel()

    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var playerStyle by remember { mutableStateOf(prefs.getPlayerStyle()) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var achievementPopupsEnabled by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showPlayerStyleDialog by remember { mutableStateOf(false) }
    var showStartDestDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.pref_theme_mode)) },
            text = {
                Column {
                    ThemeRadioButton(stringResource(R.string.theme_system), AppThemeMode.SYSTEM, themeMode) { themeMode = it; prefs.setThemeMode(it); showThemeDialog = false }
                    ThemeRadioButton(stringResource(R.string.theme_light), AppThemeMode.LIGHT, themeMode) { themeMode = it; prefs.setThemeMode(it); showThemeDialog = false }
                    ThemeRadioButton(stringResource(R.string.theme_dark), AppThemeMode.DARK, themeMode) { themeMode = it; prefs.setThemeMode(it); showThemeDialog = false }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showStartDestDialog) {
        AlertDialog(
            onDismissRequest = { showStartDestDialog = false },
            title = { Text(stringResource(R.string.pref_start_screen)) },
            text = {
                Column {
                    StartDestRadioButton(stringResource(R.string.nav_home), StartDestination.HOME, startDestination) { startDestination = it; prefs.setStartDestination(it); showStartDestDialog = false }
                    StartDestRadioButton(stringResource(R.string.nav_library), StartDestination.LIBRARY, startDestination) { startDestination = it; prefs.setStartDestination(it); showStartDestDialog = false }
                }
            },
            confirmButton = { TextButton(onClick = { showStartDestDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showPlayerStyleDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerStyleDialog = false },
            title = { Text(stringResource(R.string.pref_player_style)) },
            text = {
                Column {
                    PlayerStyleRadioButton(stringResource(R.string.style_theme), PlayerBackgroundStyle.THEME, playerStyle) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                    PlayerStyleRadioButton(stringResource(R.string.style_gradient), PlayerBackgroundStyle.GRADIENT, playerStyle) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                    PlayerStyleRadioButton(stringResource(R.string.style_blur), PlayerBackgroundStyle.BLUR, playerStyle) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                }
            },
            confirmButton = { TextButton(onClick = { showPlayerStyleDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.pref_language)) },
            text = {
                Column {
                    LanguageRadioButton(stringResource(R.string.theme_system), AppLanguage.SYSTEM, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(stringResource(R.string.lang_french), AppLanguage.FRENCH, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(stringResource(R.string.lang_english), AppLanguage.ENGLISH, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(stringResource(R.string.lang_hungarian), AppLanguage.HUNGARIAN, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_appearance_title)) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back)) } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCategoryHeader(stringResource(R.string.settings_cat_general))

            SettingsItemWithSubtitle(
                icon = Icons.Rounded.Home,
                title = stringResource(R.string.pref_start_screen),
                subtitle = if (startDestination == StartDestination.HOME) stringResource(R.string.nav_home) else stringResource(R.string.nav_library),
                onClick = { showStartDestDialog = true }
            )

            SettingsItemWithSubtitle(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.pref_language),
                subtitle = stringResource(R.string.pref_language_sub),
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingsCategoryHeader(stringResource(R.string.settings_cat_appearance))

            SwitchSettingItem(
                icon = Icons.Rounded.ColorLens,
                title = stringResource(R.string.pref_theme_dynamic),
                subtitle = stringResource(R.string.pref_theme_dynamic_sub),
                checked = dynamicTheme,
                onCheckedChange = { dynamicTheme = it; prefs.setDynamicTheme(it) }
            )

            SettingsItemWithSubtitle(
                icon = Icons.Rounded.DarkMode,
                title = stringResource(R.string.pref_theme_mode),
                subtitle = when(themeMode) {
                    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                },
                onClick = { showThemeDialog = true }
            )

            SwitchSettingItem(
                icon = Icons.Rounded.DarkMode,
                title = stringResource(R.string.pref_theme_pure_black),
                subtitle = stringResource(R.string.pref_theme_pure_black_sub),
                checked = pureBlack,
                enabled = themeMode != AppThemeMode.LIGHT,
                onCheckedChange = { pureBlack = it; prefs.setPureBlack(it) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingsCategoryHeader(stringResource(R.string.settings_cat_playback))

            SettingsItemWithSubtitle(
                icon = Icons.Rounded.Wallpaper,
                title = stringResource(R.string.pref_player_style),
                subtitle = when(playerStyle) {
                    PlayerBackgroundStyle.THEME -> stringResource(R.string.style_theme)
                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.style_gradient)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.style_blur)
                },
                onClick = { showPlayerStyleDialog = true }
            )

            // removed duplicate category here

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingsCategoryHeader(stringResource(R.string.settings_cat_notifications))

            SwitchSettingItem(
                icon = Icons.Rounded.EmojiEvents,
                title = stringResource(R.string.pref_achievement_popups),
                subtitle = stringResource(R.string.pref_achievement_popups_sub),
                checked = achievementPopupsEnabled,
                onCheckedChange = {
                    achievementPopupsEnabled = it
                    prefs.setAchievementPopupsEnabled(it)
                }
            )

            // fix padding overlap with mini player
            if (playerViewModel.currentTrack != null) {
                Spacer(modifier = Modifier.height(80.dp)) // 64dp player + 16dp margin
            }
        }
    }
}

fun restartApp(context: Context) {
    if (context is Activity) {
        context.recreate()
    } else {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}

@Composable
fun ThemeRadioButton(text: String, mode: AppThemeMode, selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (mode == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun PlayerStyleRadioButton(text: String, style: PlayerBackgroundStyle, selected: PlayerBackgroundStyle, onSelect: (PlayerBackgroundStyle) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(style) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (style == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun StartDestRadioButton(text: String, dest: StartDestination, selected: StartDestination, onSelect: (StartDestination) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(dest) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (dest == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun LanguageRadioButton(text: String, lang: AppLanguage, selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(lang) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (lang == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}