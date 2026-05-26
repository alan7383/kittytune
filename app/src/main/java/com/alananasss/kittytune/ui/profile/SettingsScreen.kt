    package com.alananasss.kittytune.ui.profile
    
    import androidx.compose.foundation.layout.PaddingValues
    import androidx.compose.ui.res.vectorResource
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.SdStorage
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.unit.dp
    import androidx.navigation.NavController
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    
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
    
            val miniPlayerHeight = if (playerViewModel.currentTrack != null) 64.dp else 0.dp
            LazyColumn(
                modifier = Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + miniPlayerHeight + 150.dp, top = 16.dp)
            ) {
    
                item {
                    SettingsGroup(
                        title = stringResource(R.string.settings_cat_appearance),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_appearance_title),
                                    subtitle = stringResource(R.string.pref_appearance_subtitle),
                                    icon = Icons.Rounded.Palette,
                                    onClick = { navController.navigate("appearance_settings") }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_lyrics_title),
                                    subtitle = stringResource(R.string.pref_lyrics_subtitle),
                                    icon = Icons.Rounded.TextSnippet,
                                    onClick = { navController.navigate("lyrics_settings") }
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
                                    title = stringResource(R.string.pref_audio_title),
                                    subtitle = stringResource(R.string.pref_audio_subtitle),
                                    icon = Icons.Rounded.GraphicEq,
                                    onClick = { navController.navigate("audio_settings") }
                                )
                            }
                        )
                    )
                }
    
                item {
                    SettingsGroup(
                        title = stringResource(R.string.settings_cat_general),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_discord_title),
                                    subtitle = stringResource(R.string.pref_discord_subtitle),
                                    icon = ImageVector.vectorResource(id = R.drawable.ic_discord),
                                    onClick = { navController.navigate("discord_settings") }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_local_title),
                                    subtitle = stringResource(R.string.pref_local_subtitle),
                                    icon = Icons.Filled.SdStorage,
                                    onClick = { navController.navigate("local_media_settings") }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_storage_title),
                                    subtitle = stringResource(R.string.pref_storage_subtitle),
                                    icon = Icons.Rounded.Storage,
                                    onClick = { navController.navigate("storage") }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_backup_title),
                                    subtitle = stringResource(R.string.pref_backup_subtitle),
                                    icon = Icons.Rounded.Backup,
                                    onClick = { navController.navigate("backup_restore") }
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
    


