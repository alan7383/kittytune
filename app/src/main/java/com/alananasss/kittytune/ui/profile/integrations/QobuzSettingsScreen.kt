package com.alananasss.kittytune.ui.profile.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import java.util.Locale

@Composable
fun QobuzSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var qobuzCountry by remember { mutableStateOf(prefs.getQobuzCountry()) }
    var qobuzCustomInstances by remember { mutableStateOf(prefs.getQobuzCustomInstances()) }
    var qobuzQuality by remember { mutableStateOf(prefs.getQobuzQuality()) }

    var showCountryDialog by remember { mutableStateOf(false) }
    var showInstancesDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    if (showCountryDialog) {
        var tempCountry by remember { mutableStateOf(qobuzCountry) }
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_country),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = tempCountry,
                        onValueChange = { tempCountry = it.uppercase(Locale.US).take(2) },
                        placeholder = { Text("US") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.qobuz_country_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalCountry = tempCountry.trim().uppercase(Locale.US).ifBlank { "US" }
                        qobuzCountry = finalCountry
                        prefs.setQobuzCountry(finalCountry)
                        showCountryDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCountryDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showInstancesDialog) {
        var tempInstances by remember { mutableStateOf(qobuzCustomInstances) }
        AlertDialog(
            onDismissRequest = { showInstancesDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_custom_instances),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = tempInstances,
                        onValueChange = { tempInstances = it },
                        placeholder = { Text(stringResource(R.string.qobuz_custom_instances_placeholder)) },
                        singleLine = false,
                        maxLines = 8,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.qobuz_custom_instances_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = tempInstances.trim()
                        qobuzCustomInstances = cleaned
                        prefs.setQobuzCustomInstances(cleaned)
                        showInstancesDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstancesDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_audio_quality),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    listOf(
                        27 to R.string.qobuz_quality_hires_192,
                        7 to R.string.qobuz_quality_hires_96,
                        6 to R.string.qobuz_quality_cd,
                        5 to R.string.qobuz_quality_mp3_320
                    ).forEach { (code, labelRes) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    qobuzQuality = code
                                    prefs.setQobuzQuality(code)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = qobuzQuality == code,
                                onClick = {
                                    qobuzQuality = code
                                    prefs.setQobuzQuality(code)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
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

    SettingsScaffold(
        title = stringResource(R.string.qobuz_integration),
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
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_country),
                                subtitle = stringResource(R.string.qobuz_country_desc),
                                icon = Icons.Rounded.Language,
                                onClick = { showCountryDialog = true }
                            )
                        },
                        { shape ->
                            val instances = qobuzCustomInstances.split("\n").filter { it.isNotBlank() }
                            val desc = if (instances.isEmpty() || qobuzCustomInstances == QobuzAudioProvider.DEFAULT_INSTANCE) {
                                stringResource(R.string.qobuz_custom_instances_desc_default)
                            } else {
                                stringResource(R.string.qobuz_custom_instances_desc_custom, instances.size)
                            }
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_custom_instances),
                                subtitle = desc,
                                icon = Icons.Rounded.Link,
                                onClick = { showInstancesDialog = true }
                            )
                        },
                        { shape ->
                            val qualityLabel = when (qobuzQuality) {
                                27 -> stringResource(R.string.qobuz_quality_hires_192)
                                7 -> stringResource(R.string.qobuz_quality_hires_96)
                                6 -> stringResource(R.string.qobuz_quality_cd)
                                else -> stringResource(R.string.qobuz_quality_mp3_320)
                            }
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_audio_quality),
                                subtitle = qualityLabel,
                                icon = Icons.Rounded.GraphicEq,
                                onClick = { showQualityDialog = true }
                            )
                        }
                    )
                )
            }
        }
    }
}
