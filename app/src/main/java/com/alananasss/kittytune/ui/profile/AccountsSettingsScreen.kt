package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality
import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality
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
    onNavigateToDiscord: () -> Unit,
    onNavigateToProviderOrder: () -> Unit,
    onNavigateToQobuz: () -> Unit,
    onNavigateToTidal: () -> Unit,
    onNavigateToDeezer: () -> Unit
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

    val order = prefs.getAudioProviderOrder()
    val orderSummary = order.joinToString(", ") { item ->
        when (item) {
            AudioProviderOrderItem.QOBUZ -> "Qobuz"
            AudioProviderOrderItem.TIDAL -> "TIDAL"
            AudioProviderOrderItem.DEEZER -> "Deezer"
            AudioProviderOrderItem.YOUTUBE_MUSIC -> "YouTube Music"
            AudioProviderOrderItem.SOUNDCLOUD -> "SoundCloud"
        }
    }

    val qobuzInstances = prefs.getQobuzCustomInstances().split("\n").filter { it.isNotBlank() }
    val qobuzSubtitle = if (qobuzInstances.isEmpty() || prefs.getQobuzCustomInstances() == QobuzAudioProvider.DEFAULT_INSTANCE) {
        "${prefs.getQobuzCountry()} • ${stringResource(R.string.qobuz_custom_instances_desc_default)}"
    } else {
        "${prefs.getQobuzCountry()} • ${stringResource(R.string.qobuz_custom_instances_desc_custom, qobuzInstances.size)}"
    }

    val tidalQuality = prefs.getTidalAudioQuality()
    val tidalSubtitle = when (tidalQuality) {
        TidalAudioQuality.AAC_320 -> stringResource(R.string.tidal_quality_aac_320)
        TidalAudioQuality.FLAC -> stringResource(R.string.tidal_quality_flac)
        TidalAudioQuality.HI_RES_LOSSLESS -> stringResource(R.string.tidal_quality_hires)
    }

    val deezerQuality = prefs.getDeezerAudioQuality()
    val deezerSubtitle = when (deezerQuality) {
        DeezerAudioQuality.FLAC -> stringResource(R.string.deezer_quality_flac)
        DeezerAudioQuality.MP3_320 -> stringResource(R.string.deezer_quality_mp3_320)
        DeezerAudioQuality.MP3_128 -> stringResource(R.string.deezer_quality_mp3_128)
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

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsGroup(
                    title = stringResource(R.string.audio_providers_title),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.provider_order),
                                subtitle = orderSummary,
                                icon = Icons.Rounded.SwapVert,
                                onClick = onNavigateToProviderOrder
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_integration),
                                subtitle = qobuzSubtitle,
                                iconRes = R.drawable.ic_logo_qobuz,
                                onClick = onNavigateToQobuz
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.tidal_integration),
                                subtitle = tidalSubtitle,
                                iconRes = R.drawable.ic_logo_tidal,
                                onClick = onNavigateToTidal
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.deezer_integration),
                                subtitle = deezerSubtitle,
                                iconRes = R.drawable.ic_logo_deezer,
                                onClick = onNavigateToDeezer
                            )
                        }
                    )
                )
            }
        }
    }
}
