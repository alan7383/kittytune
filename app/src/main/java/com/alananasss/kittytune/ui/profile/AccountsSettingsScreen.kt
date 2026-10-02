package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality
import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality
import com.alananasss.kittytune.data.DataSaver
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.AutoScrollToHighlightedItem
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
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
    val disabledProviders = prefs.getDisabledAudioProviders()
    val disabledSuffix = stringResource(R.string.provider_disabled_suffix)
    val orderSummary = order.joinToString(", ") { item ->
        val name = when (item) {
            AudioProviderOrderItem.QOBUZ -> "Qobuz"
            AudioProviderOrderItem.TIDAL -> "TIDAL"
            AudioProviderOrderItem.DEEZER -> "Deezer"
            AudioProviderOrderItem.YOUTUBE_MUSIC -> "YouTube Music"
            AudioProviderOrderItem.SOUNDCLOUD -> "SoundCloud"
        }
        if (item in disabledProviders) "$name $disabledSuffix" else name
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

    // ─── Quality & data (the traffic controls moved here from the audio screen) ──
    var dataSaverEnabled by remember { mutableStateOf(prefs.getDataSaverEnabled()) }
    var dataSaverMeteredOnly by remember { mutableStateOf(prefs.getDataSaverMeteredOnly()) }
    var audioQuality by remember { mutableStateOf(prefs.getAudioQuality()) }
    var showQualityDialog by remember { mutableStateOf(false) }
    // Re-computed on every recomposition of the screen: flipping either switch above
    // re-reads it, so the forced-eco state never goes stale while the user is looking.
    val dataSaverActive = DataSaver.isActive(context)

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.pref_quality)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                audioQuality = "HIGH"
                                prefs.setAudioQuality("HIGH")
                                showQualityDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioQuality == "HIGH", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.quality_high), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.quality_high_sub), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                audioQuality = "LOW"
                                prefs.setAudioQuality("LOW")
                                showQualityDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioQuality == "LOW", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.quality_low), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.quality_low_sub), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    val listState = rememberLazyListState()

    AutoScrollToHighlightedItem(
        listState = listState,
        keyToIndex = mapOf(
            "pref_account_soundcloud" to 0,
            "pref_account_vk" to 0,
            "pref_discord" to 0,
            "pref_provider_order" to 1,
            "pref_qobuz" to 1,
            "pref_tidal" to 1,
            "pref_deezer" to 1,
            "pref_data_saver" to 2,
            "pref_quality" to 2
        )
    )

    SettingsScaffold(
        title = stringResource(R.string.accounts_screen_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            state = listState,
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
                                onClick = onNavigateToSoundCloud,
                                highlightKey = "pref_account_soundcloud"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_account_vk_title),
                                subtitle = vkSubtitle,
                                iconRes = R.drawable.ic_vk,
                                onClick = onNavigateToVk,
                                highlightKey = "pref_account_vk"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_discord_title),
                                subtitle = if (isDiscordLoggedIn) {
                                    val discordUsername = prefs.getDiscordUsername()
                                    if (!discordUsername.isNullOrEmpty()) {
                                        stringResource(R.string.discord_connected_as, discordUsername)
                                    } else {
                                        stringResource(R.string.discord_connected)
                                    }
                                } else {
                                    stringResource(R.string.discord_not_connected)
                                },
                                iconRes = R.drawable.ic_discord,
                                onClick = onNavigateToDiscord,
                                highlightKey = "pref_discord"
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
                                onClick = onNavigateToProviderOrder,
                                highlightKey = "pref_provider_order"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_integration),
                                subtitle = qobuzSubtitle,
                                iconRes = R.drawable.ic_logo_qobuz,
                                onClick = onNavigateToQobuz,
                                highlightKey = "pref_qobuz"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.tidal_integration),
                                subtitle = tidalSubtitle,
                                iconRes = R.drawable.ic_logo_tidal,
                                onClick = onNavigateToTidal,
                                highlightKey = "pref_tidal"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.deezer_integration),
                                subtitle = deezerSubtitle,
                                iconRes = R.drawable.ic_logo_deezer,
                                onClick = onNavigateToDeezer,
                                highlightKey = "pref_deezer"
                            )
                        }
                    )
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.quality_data_section))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // The one traffic switch: eco streams everywhere, light covers,
                        // no animated artwork. Nested item appears only while it is on.
                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 4.dp
                            ),
                            title = stringResource(R.string.data_saver_title),
                            subtitle = stringResource(R.string.data_saver_sub),
                            icon = Icons.Rounded.DataSaverOn,
                            hasSwitch = true,
                            switchState = dataSaverEnabled,
                            onSwitchChange = { dataSaverEnabled = it; prefs.setDataSaverEnabled(it) },
                            highlightKey = "pref_data_saver"
                        )

                        AnimatedVisibility(
                            visible = dataSaverEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = RoundedCornerShape(4.dp),
                                title = stringResource(R.string.data_saver_metered_only),
                                subtitle = stringResource(R.string.data_saver_metered_only_sub),
                                hasSwitch = true,
                                switchState = dataSaverMeteredOnly,
                                onSwitchChange = { dataSaverMeteredOnly = it; prefs.setDataSaverMeteredOnly(it) }
                            )
                        }

                        // Stream quality, moved here from the audio screen. While the data
                        // saver is active the choice is overridden — the row reflects that
                        // and stops opening the dialog until the saver lets go.
                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 4.dp,
                                bottomStart = 24.dp,
                                bottomEnd = 24.dp
                            ),
                            title = stringResource(R.string.pref_quality),
                            subtitle = when {
                                dataSaverActive -> stringResource(R.string.data_saver_forced_eco)
                                audioQuality == "HIGH" -> stringResource(R.string.quality_high)
                                else -> stringResource(R.string.quality_low)
                            },
                            icon = Icons.Rounded.GraphicEq,
                            onClick = { showQualityDialog = true }.takeUnless { dataSaverActive },
                            highlightKey = "pref_quality"
                        )
                    }
                }
            }
        }
    }
}
