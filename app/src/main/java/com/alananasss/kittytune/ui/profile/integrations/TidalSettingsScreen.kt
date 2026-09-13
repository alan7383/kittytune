package com.alananasss.kittytune.ui.profile.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.tidal.TidalAudioProvider
import com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality
import com.alananasss.kittytune.audio.providers.tidal.isTidalCookieConfigured
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

@Composable
fun TidalSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var audioQuality by remember { mutableStateOf(prefs.getTidalAudioQuality()) }
    var resolverEndpoints by remember { mutableStateOf(prefs.getTidalResolverEndpoints()) }
    var tidalCookie by remember { mutableStateOf(prefs.getTidalCookie()) }

    LaunchedEffect(Unit) {
        tidalCookie = prefs.getTidalCookie()
    }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showResolverEndpointsDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.tidal_audio_quality)) },
            text = {
                Column {
                    TidalAudioQuality.entries.forEach { quality ->
                        val label = when (quality) {
                            TidalAudioQuality.AAC_320 -> stringResource(R.string.tidal_quality_aac_320)
                            TidalAudioQuality.FLAC -> stringResource(R.string.tidal_quality_flac)
                            TidalAudioQuality.HI_RES_LOSSLESS -> stringResource(R.string.tidal_quality_hires)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    audioQuality = quality
                                    prefs.setTidalAudioQuality(quality)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = audioQuality == quality,
                                onClick = {
                                    audioQuality = quality
                                    prefs.setTidalAudioQuality(quality)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showResolverEndpointsDialog) {
        var tempEndpoints by remember { mutableStateOf(resolverEndpoints) }
        AlertDialog(
            onDismissRequest = { showResolverEndpointsDialog = false },
            title = { Text(stringResource(R.string.tidal_resolver_endpoints)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.tidal_resolver_endpoints_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempEndpoints,
                        onValueChange = { tempEndpoints = it },
                        placeholder = { Text(stringResource(R.string.tidal_resolver_endpoints_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = TidalAudioProvider.normalizeResolverEndpointsInput(tempEndpoints)
                        resolverEndpoints = cleaned
                        prefs.setTidalResolverEndpoints(cleaned)
                        showResolverEndpointsDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResolverEndpointsDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showCookieDialog) {
        var tempCookie by remember { mutableStateOf(tidalCookie) }
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text(stringResource(R.string.tidal_cookie_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.tidal_cookie_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempCookie,
                        onValueChange = { tempCookie = it },
                        placeholder = { Text(stringResource(R.string.tidal_cookie_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = tempCookie.trim()
                        tidalCookie = cleaned
                        prefs.setTidalCookie(cleaned)
                        showCookieDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val customEndpointsCount = remember(resolverEndpoints) {
        TidalAudioProvider.resolverEndpointBases(resolverEndpoints).size
    }

    val qualityLabel = when (audioQuality) {
        TidalAudioQuality.AAC_320 -> stringResource(R.string.tidal_quality_aac_320)
        TidalAudioQuality.FLAC -> stringResource(R.string.tidal_quality_flac)
        TidalAudioQuality.HI_RES_LOSSLESS -> stringResource(R.string.tidal_quality_hires)
    }

    SettingsScaffold(
        title = stringResource(R.string.tidal_integration),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            item {
                val cookieConfigured = isTidalCookieConfigured(tidalCookie)
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.tidal_web_login),
                                subtitle = if (cookieConfigured) {
                                    stringResource(R.string.tidal_cookie_configured)
                                } else {
                                    stringResource(R.string.tidal_cookie_not_configured)
                                },
                                icon = Icons.AutoMirrored.Rounded.Login,
                                onClick = onNavigateToLogin
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.tidal_audio_quality),
                                subtitle = qualityLabel,
                                icon = Icons.Rounded.GraphicEq,
                                onClick = { showQualityDialog = true }
                            )
                        }
                        add { shape ->
                            val desc = if (customEndpointsCount > 0) {
                                stringResource(R.string.tidal_resolver_endpoints_desc_custom, customEndpointsCount)
                            } else {
                                stringResource(R.string.tidal_resolver_endpoints_desc_default)
                            }
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.tidal_resolver_endpoints),
                                subtitle = desc,
                                icon = Icons.Rounded.Link,
                                onClick = { showResolverEndpointsDialog = true }
                            )
                        }
                        add { shape ->
                            val desc = if (cookieConfigured) {
                                stringResource(R.string.tidal_cookie_configured)
                            } else {
                                stringResource(R.string.tidal_cookie_not_configured)
                            }
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.tidal_cookie_title),
                                subtitle = desc,
                                icon = Icons.Rounded.Key,
                                onClick = { showCookieDialog = true }
                            )
                        }
                        if (tidalCookie.isNotBlank()) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.tidal_clear_cookie),
                                    subtitle = stringResource(R.string.tidal_cookie_configured),
                                    icon = Icons.Rounded.Delete,
                                    onClick = {
                                        tidalCookie = ""
                                        prefs.setTidalCookie("")
                                    }
                                )
                            }
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    InfoLabel(text = stringResource(R.string.tidal_web_login_desc))
                }
            }
        }
    }
}

@Composable
private fun InfoLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
