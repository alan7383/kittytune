    package com.alananasss.kittytune.ui.widget

    import android.app.Activity
    import android.appwidget.AppWidgetManager
    import android.content.Intent
    import android.os.Bundle
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.Check
    import androidx.compose.material.icons.rounded.Delete
    import androidx.compose.material.icons.rounded.Speed
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.glance.appwidget.GlanceAppWidgetManager
    import androidx.glance.appwidget.state.updateAppWidgetState
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.ui.theme.SoundTuneTheme
    import kotlinx.coroutines.MainScope
    import kotlinx.coroutines.launch
    import java.util.Locale
    import kotlin.math.roundToInt

    // Model for custom speed buttons
    data class CustomSpeedButton(val label: String, val value: Float)

    class WidgetConfigActivity : ComponentActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val appWidgetId = intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish()
                return
            }

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_CANCELED, resultValue)

            setContent {
                SoundTuneTheme {
                    WidgetConfigScreen(
                        onConfirm = { enabledEffects, customSpeeds ->
                            saveConfigAndFinish(appWidgetId, enabledEffects, customSpeeds)
                        }
                    )
                }
            }
        }

        private fun saveConfigAndFinish(
            appWidgetId: Int,
            enabledEffects: Set<String>,
            customSpeeds: List<CustomSpeedButton>
        ) {
            val context = this
            MainScope().launch {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)

                updateAppWidgetState(context, glanceId) { prefs ->
                    // Save boolean flags for effects
                    prefs[MusicWidget.KEY_SHOW_BASS] = enabledEffects.contains("BASS")
                    prefs[MusicWidget.KEY_SHOW_8D] = enabledEffects.contains("8D")
                    prefs[MusicWidget.KEY_SHOW_MUFFLED] = enabledEffects.contains("MUFFLED")
                    prefs[MusicWidget.KEY_SHOW_REVERB] = enabledEffects.contains("REVERB")
                    prefs[MusicWidget.KEY_SHOW_PITCH] = enabledEffects.contains("PITCH")

                    // Serialize speeds to a string: "Label:Value|Label:Value"
                    val speedString = customSpeeds.joinToString("|") { "${it.label}:${it.value}" }
                    prefs[MusicWidget.KEY_CUSTOM_SPEEDS] = speedString
                }

                // Force update THIS widget instance immediately
                MusicWidget().update(context, glanceId)
                // Trigger global update to fetch current track info
                MusicWidget.update(context)

                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(Activity.RESULT_OK, resultValue)
                finish()
            }
        }
    }

    @Composable
    fun WidgetConfigScreen(onConfirm: (Set<String>, List<CustomSpeedButton>) -> Unit) {
        val enabledEffects = remember { mutableStateListOf<String>() }
        val customSpeeds = remember { mutableStateListOf<CustomSpeedButton>() }
        var showAddSpeedDialog by remember { mutableStateOf(false) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { onConfirm(enabledEffects.toSet(), customSpeeds) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.widget_config_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text(stringResource(R.string.player_special_effects), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(8.dp)) {
                            ConfigCheckboxRow(stringResource(R.string.effect_bass_boost), "BASS", enabledEffects)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ConfigCheckboxRow(stringResource(R.string.effect_8d), "8D", enabledEffects)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ConfigCheckboxRow(stringResource(R.string.effect_muffled), "MUFFLED", enabledEffects)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ConfigCheckboxRow(stringResource(R.string.effect_reverb), "REVERB", enabledEffects)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ConfigCheckboxRow(stringResource(R.string.player_pitch), "PITCH", enabledEffects)
                        }
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.player_speed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { showAddSpeedDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_create))
                        }
                    }

                    if (customSpeeds.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                // fallback container if surfaceContainerLow isn't available
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(customSpeeds) { speedItem ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(speedItem.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${speedItem.value}x", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { customSpeeds.remove(speedItem) }) {
                                Icon(Icons.Rounded.Delete, stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (showAddSpeedDialog) {
            AddSpeedDialog(
                onDismiss = { showAddSpeedDialog = false },
                onAdd = { label, value ->
                    customSpeeds.add(CustomSpeedButton(label, value))
                    showAddSpeedDialog = false
                }
            )
        }
    }

    @Composable
    fun ConfigCheckboxRow(label: String, key: String, selectedList: MutableList<String>) {
        val isSelected = selectedList.contains(key)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isSelected) selectedList.remove(key) else selectedList.add(key) }
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { if (it) selectedList.add(key) else selectedList.remove(key) }
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    fun AddSpeedDialog(onDismiss: () -> Unit, onAdd: (String, Float) -> Unit) {
        var label by remember { mutableStateOf("") }
        var speedValue by remember { mutableFloatStateOf(1.2f) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.player_speed)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.dialog_speed_label_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    // HEADER VALUE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.dialog_speed_value_title), style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = String.format(Locale.US, "%.2fx", speedValue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // SLIDER
                    Slider(
                        value = speedValue,
                        onValueChange = { speedValue = it },
                        valueRange = 0.5f..2.0f,
                        steps = 29, // Allows jumps of 0.05
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.5x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("2.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (label.isNotBlank()) {
                        // Round properly to 2 decimals
                        val roundedValue = (speedValue * 20).roundToInt() / 20f
                        onAdd(label, roundedValue)
                    }
                }) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

