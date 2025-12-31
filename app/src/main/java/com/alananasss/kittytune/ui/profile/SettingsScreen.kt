package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel // needed to check for mini player
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_close))
                    }
                }
            )
        }
    ) { innerPadding ->

        // calculate padding so mini player doesn't hide the last item
        val miniPlayerHeight = if (playerViewModel.currentTrack != null) 64.dp else 0.dp
        val bottomPadding = miniPlayerHeight + 16.dp

        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            item {
                SettingsCategoryHeader(stringResource(R.string.settings_cat_appearance))

                SettingsItem(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.pref_appearance_title),
                    subtitle = stringResource(R.string.pref_appearance_subtitle),
                    onClick = { navController.navigate("appearance_settings") }
                )

                SettingsItem(
                    icon = Icons.Rounded.TextSnippet,
                    title = stringResource(R.string.pref_lyrics_title),
                    subtitle = stringResource(R.string.pref_lyrics_subtitle),
                    onClick = { navController.navigate("lyrics_settings") }
                )

                SettingsCategoryHeader(stringResource(R.string.settings_cat_playback))

                SettingsItem(
                    icon = Icons.Rounded.GraphicEq,
                    title = stringResource(R.string.pref_audio_title),
                    subtitle = stringResource(R.string.pref_audio_subtitle),
                    onClick = { navController.navigate("audio_settings") }
                )

                SettingsCategoryHeader(stringResource(R.string.settings_cat_general))

                SettingsItem(
                    icon = Icons.Filled.SdStorage,
                    title = stringResource(R.string.pref_local_title),
                    subtitle = stringResource(R.string.pref_local_subtitle),
                    onClick = { navController.navigate("local_media_settings") }
                )

                SettingsItem(
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.pref_storage_title),
                    subtitle = stringResource(R.string.pref_storage_subtitle),
                    onClick = { navController.navigate("storage") }
                )

                SettingsItem(
                    icon = Icons.Rounded.Backup,
                    title = stringResource(R.string.pref_backup_title),
                    subtitle = stringResource(R.string.pref_backup_subtitle),
                    onClick = { navController.navigate("backup_restore") }
                )

                SettingsItem(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.pref_about_title),
                    subtitle = stringResource(R.string.pref_about_subtitle),
                    onClick = { navController.navigate("about") }
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    titleColor: Color = Color.Unspecified
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium, color = titleColor) },
        supportingContent = { if (subtitle != null) Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsItemWithSubtitle(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        supportingContent = {
            Text(
                subtitle,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}