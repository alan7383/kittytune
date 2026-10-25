package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.utils.AppLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeveloperOptionsScreen(
    viewModel: PlayerViewModel? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PlayerPreferences(context) }
    var automixDebugOverlay by remember { mutableStateOf(prefs.getAutomixDebugOverlayEnabled()) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var aiCacheClearedTrigger by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()

    SettingsScaffold(
        title = stringResource(R.string.pref_developer_options_title),
        subtitle = stringResource(R.string.pref_developer_options_subtitle),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp)
        ) {
            // Audio & Transitions Debug
            item {
                SettingsGroup(
                    title = stringResource(R.string.dev_section_audio_debug),
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.automix_debug),
                                subtitle = stringResource(R.string.automix_debug_desc),
                                icon = Icons.Rounded.GraphicEq,
                                hasSwitch = true,
                                switchState = automixDebugOverlay,
                                onSwitchChange = {
                                    automixDebugOverlay = it
                                    if (it && !prefs.getAutomixEnabled()) {
                                        prefs.setAutomixEnabled(true)
                                    }
                                    prefs.setAutomixDebugOverlayEnabled(it)
                                }
                            )
                        }
                        if (viewModel != null) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.dev_dj_debug_sheet),
                                    subtitle = stringResource(R.string.dev_dj_debug_sheet_desc),
                                    icon = Icons.Rounded.BugReport,
                                    onClick = hapticAction(HapticKind.UI) {
                                        viewModel.showDjDebugSheet = true
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.dev_dj_debug_sheet),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                )
            }

            // AI Detection Diagnostics
            item {
                Spacer(Modifier.height(16.dp))
                val verdictCount = remember(aiCacheClearedTrigger) { AiDetectionManager.trackVerdictCache.size }
                SettingsGroup(
                    title = stringResource(R.string.dev_section_ai_debug),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.dev_clear_ai_cache),
                                subtitle = "${stringResource(R.string.dev_clear_ai_cache_desc)} (${verdictCount} cached)",
                                icon = Icons.Rounded.AutoAwesome,
                                onClick = hapticAction(HapticKind.UI) {
                                    AiDetectionManager.clearCache()
                                    aiCacheClearedTrigger++
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.dev_ai_cache_cleared),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    )
                )
            }

            // Logs & Diagnostics
            item {
                Spacer(Modifier.height(16.dp))
                SettingsGroup(
                    title = stringResource(R.string.dev_section_logs),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.dev_view_logs),
                                subtitle = stringResource(R.string.dev_view_logs_desc),
                                icon = Icons.Rounded.Info,
                                onClick = hapticAction(HapticKind.UI) { showLogsSheet = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.dev_export_logs),
                                subtitle = stringResource(R.string.dev_export_logs_desc),
                                icon = Icons.Rounded.Share,
                                onClick = hapticAction(HapticKind.UI) {
                                    scope.launch(Dispatchers.IO) {
                                        val report = AppLogManager.getFullReport(context)
                                        withContext(Dispatchers.Main) {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "KittyTune Diagnostics Report")
                                                putExtra(Intent.EXTRA_TEXT, report)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share KittyTune Logs"))
                                        }
                                    }
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.dev_clear_logs),
                                subtitle = stringResource(R.string.dev_clear_logs),
                                icon = Icons.Rounded.Delete,
                                onClick = hapticAction(HapticKind.UI) {
                                    AppLogManager.clearLastCrash(context)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.dev_logs_cleared),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    )
                )
            }
        }
    }

    if (showLogsSheet) {
        AppLogsSheet(
            onDismissRequest = { showLogsSheet = false }
        )
    }
}
