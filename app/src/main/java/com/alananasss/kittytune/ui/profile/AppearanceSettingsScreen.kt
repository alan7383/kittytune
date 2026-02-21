    package com.alananasss.kittytune.ui.profile
    
    import android.app.Activity
    import android.content.Context
    import android.content.Intent
    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.animation.expandVertically
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
    import androidx.compose.animation.shrinkVertically
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.isSystemInDarkTheme
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Shape
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.unit.dp
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.local.AppLanguage
    import com.alananasss.kittytune.data.local.AppThemeMode
    import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.data.local.StartDestination
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsGroupTitle
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import com.alananasss.kittytune.ui.common.getSettingsShape
    
    @Composable
    fun AppearanceSettingsScreen(
        onBackClick: () -> Unit
    ) {
        val context = LocalContext.current
        val prefs = remember { PlayerPreferences(context) }
        val isSystemDark = isSystemInDarkTheme()
    
        var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
        var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }
        var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
        var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
        var playerStyle by remember { mutableStateOf(prefs.getPlayerStyle()) }
        var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
        var achievementPopupsEnabled by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }
        var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    
        var showThemeDialog by remember { mutableStateOf(false) }
        var showPlayerStyleDialog by remember { mutableStateOf(false) }
        var showStartDestDialog by remember { mutableStateOf(false) }
        var showLanguageDialog by remember { mutableStateOf(false) }
    
        // Logic to determine if pure black option should be visible
        val isPureBlackVisible = themeMode == AppThemeMode.DARK || (themeMode == AppThemeMode.SYSTEM && isSystemDark)
    
        // --- DIALOGS ---
    
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
    
        // --- MAIN UI ---
    
        SettingsScaffold(
            title = stringResource(R.string.pref_appearance_title),
            onBackClick = onBackClick
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
    
                // SECTION: GENERAL
                item {
                    SettingsGroup(
                        title = stringResource(R.string.settings_cat_general),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_start_screen),
                                    subtitle = if (startDestination == StartDestination.HOME) stringResource(R.string.nav_home) else stringResource(R.string.nav_library),
                                    icon = Icons.Rounded.Home,
                                    onClick = { showStartDestDialog = true }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_language),
                                    subtitle = stringResource(R.string.pref_language_sub),
                                    icon = Icons.Rounded.Language,
                                    onClick = { showLanguageDialog = true }
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
    
                // SECTION: APPEARANCE (Custom implementation for Animation)
                item {
                    // This parent Column adds the necessary padding for the title and the items below
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingsGroupTitle(stringResource(R.string.settings_cat_appearance))
    
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    
                            // Total items visible in this group (changes dynamically)
                            val totalVisibleItems = if (isPureBlackVisible) 3 else 2
    
                            // 1. Dynamic Theme (Always Top)
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 0),
                                title = stringResource(R.string.pref_theme_dynamic),
                                subtitle = stringResource(R.string.pref_theme_dynamic_sub),
                                icon = Icons.Rounded.ColorLens,
                                hasSwitch = true,
                                switchState = dynamicTheme,
                                onSwitchChange = { dynamicTheme = it; prefs.setDynamicTheme(it) }
                            )
    
                            // 2. Theme Mode
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 1),
                                title = stringResource(R.string.pref_theme_mode),
                                subtitle = when(themeMode) {
                                    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                                },
                                icon = Icons.Rounded.DarkMode,
                                onClick = { showThemeDialog = true }
                            )
    
                            // 3. Pure Black (Animated)
                            AnimatedVisibility(
                                visible = isPureBlackVisible,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                SettingsItem(
                                    shape = getSettingsShape(3, 2), // Always Bottom Shape when visible
                                    title = stringResource(R.string.pref_theme_pure_black),
                                    subtitle = stringResource(R.string.pref_theme_pure_black_sub),
                                    icon = Icons.Rounded.Contrast,
                                    hasSwitch = true,
                                    switchState = pureBlack,
                                    onSwitchChange = { pureBlack = it; prefs.setPureBlack(it) }
                                )
                            }
                        }
                    }
                }
    
                // SECTION: PLAYBACK VISUALS
                item {
                    SettingsGroup(
                        title = stringResource(R.string.settings_cat_playback),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_player_style),
                                    subtitle = when(playerStyle) {
                                        PlayerBackgroundStyle.THEME -> stringResource(R.string.style_theme)
                                        PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.style_gradient)
                                        PlayerBackgroundStyle.BLUR -> stringResource(R.string.style_blur)
                                    },
                                    icon = Icons.Rounded.Wallpaper,
                                    onClick = { showPlayerStyleDialog = true }
                                )
                            }
                        )
                    )
                }
    
                // SECTION: NOTIFICATIONS
                item {
                    SettingsGroup(
                        title = stringResource(R.string.settings_cat_notifications),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_achievement_popups),
                                    subtitle = stringResource(R.string.pref_achievement_popups_sub),
                                    icon = Icons.Rounded.EmojiEvents,
                                    hasSwitch = true,
                                    switchState = achievementPopupsEnabled,
                                    onSwitchChange = {
                                        achievementPopupsEnabled = it
                                        prefs.setAchievementPopupsEnabled(it)
                                    }
                                )
                            }
                        )
                    )
                }
            }
        }
    }
    
    // --- HELPER FUNCTIONS ---
    
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


