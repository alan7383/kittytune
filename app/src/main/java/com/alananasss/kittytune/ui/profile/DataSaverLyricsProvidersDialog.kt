package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.alananasss.kittytune.ui.design.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.alananasss.kittytune.ui.design.Surface
import androidx.compose.material3.Text
import com.alananasss.kittytune.ui.design.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.ui.common.SettingsSwitch

/**
 * Chooses which lyrics sources Data Saver is allowed to query. Saving an empty list is
 * deliberately impossible: a restriction with no source would silence lyrics entirely.
 */
@Composable
fun DataSaverLyricsProvidersDialog(
    prefs: PlayerPreferences,
    onDismiss: () -> Unit,
    onSaved: (List<PreferredLyricsProvider>) -> Unit,
) {
    val allProviders = remember {
        (prefs.getLyricsProviderOrder() + PreferredLyricsProvider.entries).distinct()
    }
    var selected by remember {
        mutableStateOf(prefs.getDataSaverLyricsProviders().toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_saver_lyrics_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.data_saver_lyrics_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(allProviders, key = { it.name }) { provider ->
                        val checked = provider in selected
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hapticClickable {
                                        selected = if (checked) selected - provider else selected + provider
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                SettingsSwitch(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        selected = if (on) selected + provider else selected - provider
                                    },
                                )
                            }
                        }
                    }
                }
                if (selected.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.data_saver_lyrics_select_one),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = hapticAction(HapticKind.UI) {
                    val ordered = allProviders.filter { it in selected }
                    prefs.setDataSaverLyricsProviders(ordered)
                    onSaved(ordered)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}
