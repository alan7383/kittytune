package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape

/**
 * The MISC category: everything that is not a colour, a sound, a source or a device.
 *
 * Discord, haptics, import and About, which the desktop keeps in its Miscellaneous page. Language,
 * start screen and auto-update are deliberately absent: they still live in the Appearance screen,
 * where they were before this reorganisation, so listing them here as well would show the same
 * setting in two places and let the two disagree.
 */
@Composable
fun MiscSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

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
                    title = stringResource(R.string.settings_cat_general),
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
