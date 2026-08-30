package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

@Composable
fun AccountsSettingsScreen(
    currentUser: User?,
    onBackClick: () -> Unit,
    onNavigateToSoundCloud: () -> Unit,
    onNavigateToVk: () -> Unit,
    onNavigateToDiscord: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val vkTokenManager = remember { com.alananasss.kittytune.data.vk.VkTokenManager(context) }
    val prefs = remember { PlayerPreferences(context) }

    val isGuest = tokenManager.isGuestMode()
    val isScLoggedIn = !isGuest && tokenManager.hasAccessToken()
    val isDiscordLoggedIn = !prefs.getDiscordToken().isNullOrEmpty()
    val isVkLoggedIn = vkTokenManager.isLoggedIn()

    val scSubtitle = if (isScLoggedIn) {
        val name = currentUser?.username
        if (!name.isNullOrBlank()) {
            stringResource(R.string.pref_account_soundcloud_subtitle_connected, name)
        } else {
            stringResource(R.string.account_connected_status)
        }
    } else {
        stringResource(R.string.pref_account_soundcloud_subtitle_guest)
    }

    val vkSubtitle = if (isVkLoggedIn) {
        val name = vkTokenManager.getUser()?.fullName
        if (!name.isNullOrBlank()) {
            stringResource(R.string.pref_account_vk_subtitle_connected, name)
        } else {
            stringResource(R.string.account_connected_status)
        }
    } else {
        stringResource(R.string.pref_account_vk_subtitle_guest)
    }

    SettingsScaffold(
        title = stringResource(R.string.accounts_screen_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.accounts_connected_section),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_account_soundcloud_title),
                                subtitle = scSubtitle,
                                iconRes = R.drawable.ic_soundcloud,
                                onClick = onNavigateToSoundCloud
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_account_vk_title),
                                subtitle = vkSubtitle,
                                iconRes = R.drawable.ic_vk,
                                onClick = onNavigateToVk
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_discord_title),
                                subtitle = if (isDiscordLoggedIn) {
                                    stringResource(R.string.discord_connected)
                                } else {
                                    stringResource(R.string.discord_not_connected)
                                },
                                iconRes = R.drawable.ic_discord,
                                onClick = onNavigateToDiscord
                            )
                        }
                    )
                )
            }
        }
    }
}
