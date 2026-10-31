package com.alananasss.kittytune.ui.player.audio

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.audio.AutoEqProfile
import com.alananasss.kittytune.data.audio.AutoEqRepository
import com.alananasss.kittytune.data.audio.ConnectedHeadphone
import com.alananasss.kittytune.data.audio.HeadphoneDetector
import com.alananasss.kittytune.data.audio.HeadphoneDeviceType
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.player.EqualizerPresets
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FREQUENCY_LABELS = listOf(
    "32", "64", "125", "200", "315", "500", "800", "1.2k",
    "2k", "3.1k", "5k", "8k", "10k", "12.5k", "16k", "20k"
)

private enum class EqualizerMode { MANUAL, AUTO }

/**
 * Dedicated full screen for KittyTune's 16-band graphic equalizer and AutoEQ engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eqState = viewModel.equalizerState
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val haptics = rememberAppHaptics()
    val headphones by HeadphoneDetector.device.collectAsState()
    var mode by rememberSaveable {
        mutableStateOf(if (eqState.autoProfileName != null) EqualizerMode.AUTO else EqualizerMode.MANUAL)
    }

    LaunchedEffect(Unit) { HeadphoneDetector.start(context) }

    BackHandler { onBackClick() }

    SettingsScaffold(
        title = stringResource(R.string.equalizer_title),
        subtitle = when {
            !eqState.isEnabled -> stringResource(R.string.equalizer_off)
            eqState.autoProfileName != null -> eqState.autoProfileName
            else -> eqState.selectedPreset
        },
        onBackClick = onBackClick,
        actions = {
            IconButton(
                onClick = {
                    haptics.perform(HapticKind.CONFIRM)
                    viewModel.resetEqualizer()
                },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.equalizer_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SettingsSwitch(
                checked = eqState.isEnabled,
                onCheckedChange = { enabled ->
                    if (enabled != eqState.isEnabled) viewModel.toggleEqualizer()
                }
            )
            Spacer(Modifier.width(8.dp))
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val modes = listOf(EqualizerMode.MANUAL, EqualizerMode.AUTO)
            ExpressiveConnectedButtonGroup(
                options = modes,
                selectedOption = mode,
                onOptionSelected = { mode = it },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                iconProvider = { selected ->
                    Icon(
                        imageVector = if (selected == EqualizerMode.MANUAL) Icons.Rounded.Equalizer else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                labelProvider = { selected ->
                    Text(
                        text = stringResource(
                            if (selected == EqualizerMode.MANUAL) R.string.equalizer_mode_manual
                            else R.string.equalizer_mode_auto
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            )

            when (mode) {
                EqualizerMode.MANUAL -> ManualEqualizerBody(
                    viewModel = viewModel,
                    eqState = eqState,
                    haptics = haptics,
                )
                EqualizerMode.AUTO -> AutoEqBody(
                    viewModel = viewModel,
                    context = context,
                    prefs = prefs,
                    eqState = eqState,
                    headphones = headphones,
                )
            }
        }
    }
}

@Composable
private fun ManualEqualizerBody(
    viewModel: PlayerViewModel,
    eqState: com.alananasss.kittytune.ui.player.EqualizerState,
    haptics: com.alananasss.kittytune.ui.common.AppHaptics,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Frequency Response Card (borderless, modern container)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_response_curve),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (eqState.isEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (eqState.isEnabled) eqState.selectedPreset else stringResource(R.string.equalizer_off),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (eqState.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                EqualizerCurveVisualizer(
                    bandGains = eqState.bandGainsDb,
                    isEnabled = eqState.isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                )
            }
        }

        // Presets Chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.equalizer_presets),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(EqualizerPresets.all) { preset ->
                    val isSelected = eqState.selectedPreset == preset.name && eqState.isEnabled
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.perform(HapticKind.LIGHT)
                            viewModel.applyEqualizerPreset(preset)
                            if (!eqState.isEnabled) viewModel.toggleEqualizer()
                        },
                        label = { Text(preset.name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = CircleShape,
                        border = null
                    )
                }
            }
        }

        // Preamp Card (borderless)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_preamp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (abs(eqState.preampDb) < 0.05f) "0.0 dB"
                        else String.format("%+.1f dB", eqState.preampDb),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = eqState.preampDb,
                    onValueChange = { viewModel.setEqualizerPreamp(it) },
                    valueRange = -12f..12f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Frequency Bands (borderless card)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equalizer_bands_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.equalizer_double_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0 until 16) {
                        val gain = eqState.bandGainsDb.getOrElse(i) { 0f }
                        val label = FREQUENCY_LABELS.getOrElse(i) { "" }
                        EqualizerBandColumn(
                            frequencyLabel = label,
                            gainDb = gain,
                            isEnabled = eqState.isEnabled,
                            onGainChange = { newGain -> viewModel.setEqualizerBand(i, newGain) },
                            onReset = {
                                viewModel.setEqualizerBand(i, 0f)
                                haptics.perform(HapticKind.MEDIUM)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoEqBody(
    viewModel: PlayerViewModel,
    context: android.content.Context,
    prefs: PlayerPreferences,
    eqState: com.alananasss.kittytune.ui.player.EqualizerState,
    headphones: ConnectedHeadphone?,
) {
    val haptics = rememberAppHaptics()
    var query by rememberSaveable { mutableStateOf("") }
    var autoFilledKey by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<AutoEqProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var applyingId by remember { mutableStateOf<String?>(null) }
    var applyError by remember { mutableStateOf<String?>(null) }
    var autoApply by remember { mutableStateOf(prefs.getAutoEqAutoApply()) }
    var searchNonce by remember { mutableIntStateOf(0) }
    var searchedQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val errorMessage = stringResource(R.string.equalizer_auto_error)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }.orEmpty()
                    if (rawText.isNotBlank()) {
                        val fileName = runCatching {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                            }
                        }.getOrNull() ?: "Custom EQ"
                        val profileName = fileName.removeSuffix(".txt").removeSuffix(" GraphicEQ")
                        val data = AutoEqRepository.loadFromText(profileName, rawText)
                        viewModel.applyAutoEqProfile(data, headphones)
                        haptics.perform(HapticKind.CONFIRM)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    applyError = errorMessage
                }
            }
        }
    }

    LaunchedEffect(headphones?.key) {
        val device = headphones ?: return@LaunchedEffect
        val cleanName = AutoEqRepository.cleanQuery(device.name)
        if (query.isBlank() && autoFilledKey != device.key && cleanName.isNotBlank()) {
            query = cleanName
            autoFilledKey = device.key
        }
    }

    LaunchedEffect(query, searchNonce) {
        searchError = null
        applyError = null
        if (query.trim().length < 2) {
            results = emptyList()
            isSearching = false
            searchedQuery = ""
            return@LaunchedEffect
        }
        delay(300)
        isSearching = true
        try {
            results = AutoEqRepository.search(context, query)
            searchedQuery = query
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            results = emptyList()
            searchError = errorMessage
        } finally {
            isSearching = false
        }
    }

    val applyProfile: (AutoEqProfile) -> Unit = { profile ->
        if (applyingId == null) {
            haptics.perform(HapticKind.CONFIRM)
            applyingId = profile.id
            applyError = null
            scope.launch {
                try {
                    val data = AutoEqRepository.load(context, profile)
                    viewModel.applyAutoEqProfile(data, headphones)
                    haptics.perform(HapticKind.CONFIRM)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    applyError = errorMessage
                } finally {
                    applyingId = null
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Device Card (Capsule Oval, Borderless)
        AutoEqDeviceCard(
            headphones = headphones,
            onSearchDevice = { device ->
                query = AutoEqRepository.cleanQuery(device.name)
                autoFilledKey = device.key
                haptics.perform(HapticKind.LIGHT)
            }
        )

        // 2. Active Profile (Animated smoothly, Oval, Borderless)
        AnimatedVisibility(
            visible = eqState.autoProfileName != null,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            AutoEqAppliedCard(
                name = eqState.autoProfileName ?: "",
                source = eqState.autoProfileSource,
                deviceName = eqState.autoProfileDeviceName,
                onRemove = {
                    haptics.perform(HapticKind.REJECT)
                    viewModel.removeAutoEqProfile(headphones?.key)
                }
            )
        }

        // 3. Search Bar (Pure Oval / Pill, Borderless)
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.equalizer_auto_search_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                when {
                    isSearching -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    query.isNotEmpty() -> IconButton(
                        onClick = {
                            haptics.perform(HapticKind.LIGHT)
                            query = ""
                            autoFilledKey = headphones?.key
                        },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lyrics_search_clear))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        // 4. Suggestions Chips (when query is empty)
        if (query.isBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equalizer_auto_suggestions),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalButton(
                    onClick = {
                        haptics.perform(HapticKind.LIGHT)
                        filePickerLauncher.launch("text/*")
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.equalizer_auto_import),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(POPULAR_HEADPHONES) { model ->
                    AssistChip(
                        onClick = {
                            haptics.perform(HapticKind.LIGHT)
                            query = model
                        },
                        label = { Text(model, maxLines = 1) },
                        shape = CircleShape,
                        border = null
                    )
                }
            }
        }

        // 5. Search Error Banner
        if (searchError != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = searchError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            haptics.perform(HapticKind.LIGHT)
                            searchNonce++
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.equalizer_auto_retry))
                    }
                }
            }
        }

        if (applyError != null) {
            Text(
                text = applyError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (query.trim().length >= 2 && !isSearching && searchError == null &&
            results.isEmpty() && searchedQuery.isNotBlank()
        ) {
            Text(
                text = stringResource(R.string.equalizer_auto_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 6. Search Results List (Borderless)
        if (results.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equalizer_auto_results),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${results.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                results.take(30).forEach { profile ->
                    AutoEqResultRow(
                        profile = profile,
                        isApplied = eqState.autoProfileId == profile.id,
                        isApplying = applyingId == profile.id,
                        onClick = { applyProfile(profile) }
                    )
                }
            }
        }

        // 7. "Apply automatically when this device reconnects" (Uses SettingsItem for authentic settings interaction: shared interactionSource, awake toggle, elevates on press)
        SettingsItem(
            shape = CircleShape,
            title = stringResource(R.string.equalizer_auto_restore),
            icon = Icons.Rounded.AutoAwesome,
            hasSwitch = true,
            switchState = autoApply,
            onSwitchChange = { enabled ->
                autoApply = enabled
                viewModel.setAutoEqAutoApply(enabled)
            }
        )

        Text(
            text = stringResource(R.string.equalizer_auto_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Headphone icon: differentiates between in-ear earbuds and over-ear headphones.
 */
@Composable
private fun HeadphoneThumbnail(
    modelName: String,
    formFactor: String?,
    isApplied: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val isInEar = remember(formFactor, modelName) { HeadphoneDeviceType.isInEar(formFactor, modelName) }
    val tint = if (isApplied) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSecondaryContainer
    val containerColor = if (isApplied) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondaryContainer

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isInEar) {
                Icon(
                    painter = painterResource(R.drawable.ic_earbuds),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size((size.value * 0.52f).dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size((size.value * 0.52f).dp)
                )
            }
        }
    }
}

/**
 * Connected / No-headphone device card: rendered as a sleek borderless oval pill.
 */
@Composable
private fun AutoEqDeviceCard(
    headphones: ConnectedHeadphone?,
    onSearchDevice: (ConnectedHeadphone) -> Unit,
) {
    val detected = headphones != null
    Surface(
        shape = CircleShape,
        color = if (detected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeadphoneThumbnail(
                modelName = headphones?.name ?: "",
                formFactor = null,
                isApplied = detected,
                size = 44.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (detected) R.string.equalizer_auto_detected
                        else R.string.equalizer_auto_no_device
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (detected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = headphones?.name ?: stringResource(R.string.equalizer_auto_no_device_sub),
                    style = if (detected) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (detected) FontWeight.Bold else FontWeight.Normal,
                    color = if (detected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (headphones != null) {
                    Text(
                        text = headphones.connectionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (headphones != null) {
                FilledTonalIconButton(
                    onClick = { onSearchDevice(headphones) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.equalizer_auto_search_action)
                    )
                }
            }
        }
    }
}

/**
 * Applied profile indicator card: pill shape, borderless.
 */
@Composable
private fun AutoEqAppliedCard(
    name: String,
    source: String?,
    deviceName: String?,
    onRemove: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeadphoneThumbnail(
                modelName = name,
                formFactor = null,
                isApplied = true,
                size = 38.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.equalizer_auto_applied),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(deviceName, source).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.equalizer_auto_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Result row in search: modern rounded surface, borderless.
 */
@Composable
private fun AutoEqResultRow(
    profile: AutoEqProfile,
    isApplied: Boolean,
    isApplying: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    FilledTonalButton(
        onClick = {
            haptics.perform(HapticKind.UI)
            onClick()
        },
        enabled = !isApplying,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isApplied) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HeadphoneThumbnail(
            modelName = profile.name,
            formFactor = profile.formFactor,
            isApplied = isApplied,
            size = 40.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isApplied) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profile.attribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = profile.formFactor,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isApplying) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.equalizer_auto_apply),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private val POPULAR_HEADPHONES = listOf(
    "Google Pixel Buds Pro 2",
    "Sony WH-1000XM5",
    "Apple AirPods Pro 2",
    "Samsung Galaxy Buds3 Pro",
    "Bose QuietComfort Ultra",
    "Apple AirPods 4",
    "Sony WF-1000XM5",
    "Sennheiser HD 650",
    "Anker Soundcore Space Q45",
)

/**
 * An expressive vertical slider for a single frequency band.
 */
@Composable
private fun EqualizerBandColumn(
    frequencyLabel: String,
    gainDb: Float,
    isEnabled: Boolean,
    onGainChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var lastHapticCrossZero by remember { mutableStateOf(false) }

    val trackHeight = 140.dp
    val trackWidth = 5.dp
    val thumbSize = 22.dp

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val centerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .width(46.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Gain value badge
        Text(
            text = if (abs(gainDb) < 0.2f) "0" else String.format("%+.0f", gainDb),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = if (abs(gainDb) >= 0.5f) FontWeight.Bold else FontWeight.Normal,
            color = if (isEnabled && abs(gainDb) >= 0.5f) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Custom Vertical Slider
        Box(
            modifier = Modifier
                .height(trackHeight)
                .width(42.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onReset() },
                        onTap = { offset ->
                            val heightPx = size.height.toFloat()
                            val frac = 1f - (offset.y / heightPx).coerceIn(0f, 1f)
                            val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)
                            onGainChange(newGain)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val heightPx = size.height.toFloat()
                        val frac = 1f - (change.position.y / heightPx).coerceIn(0f, 1f)
                        val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)

                        // Trigger haptic when crossing 0 dB
                        val nearZero = abs(newGain) < 0.5f
                        if (nearZero && !lastHapticCrossZero) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticCrossZero = true
                        } else if (!nearZero) {
                            lastHapticCrossZero = false
                        }

                        onGainChange(newGain)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Track & Center Line Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cW = size.width
                val cH = size.height
                val midX = cW / 2f
                val midY = cH / 2f

                val tW = trackWidth.toPx()

                // Background track
                drawRoundRect(
                    color = trackBgColor,
                    topLeft = Offset(midX - tW / 2f, 0f),
                    size = Size(tW, cH),
                    cornerRadius = CornerRadius(tW / 2f)
                )

                // Active gain fill from center (0 dB)
                val gainFrac = (gainDb / 12f).coerceIn(-1f, 1f)
                val activeH = abs(gainFrac) * (cH / 2f)

                if (isEnabled && activeH > 1f) {
                    val topY = if (gainFrac > 0) midY - activeH else midY
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(midX - tW / 2f, topY),
                        size = Size(tW, activeH),
                        cornerRadius = CornerRadius(tW / 2f)
                    )
                }

                // Center 0 dB notch tick
                drawLine(
                    color = centerLineColor,
                    start = Offset(midX - 8.dp.toPx(), midY),
                    end = Offset(midX + 8.dp.toPx(), midY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Thumb
            val normalizedPos = 1f - ((gainDb + 12f) / 24f).coerceIn(0f, 1f)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val maxHPx = with(density) { maxHeight.toPx() }
                val thumbSizePx = with(density) { thumbSize.toPx() }
                val thumbY = (normalizedPos * (maxHPx - thumbSizePx))

                Surface(
                    modifier = Modifier
                        .offset(y = with(density) { thumbY.toDp() })
                        .size(thumbSize),
                    shape = CircleShape,
                    color = if (isEnabled) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 3.dp
                ) {}
            }
        }

        // Frequency Label
        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Visual curve showing the continuous spline frequency response across the 16 bands.
 */
@Composable
private fun EqualizerCurveVisualizer(
    bandGains: List<Float>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val curveColor = if (isEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        val cW = size.width
        val cH = size.height
        val midY = cH / 2f
        val numBands = bandGains.size.coerceAtLeast(1)

        // Draw 0 dB baseline
        drawLine(
            color = zeroLineColor,
            start = Offset(0f, midY),
            end = Offset(cW, midY),
            strokeWidth = 1.dp.toPx()
        )

        // Draw +6 dB and -6 dB faint lines
        val quarterH = cH / 4f
        drawLine(
            color = zeroLineColor.copy(alpha = 0.2f),
            start = Offset(0f, midY - quarterH),
            end = Offset(cW, midY - quarterH),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = zeroLineColor.copy(alpha = 0.2f),
            start = Offset(0f, midY + quarterH),
            end = Offset(cW, midY + quarterH),
            strokeWidth = 0.8.dp.toPx()
        )

        if (numBands < 2) return@Canvas

        // Calculate control points
        val points = mutableListOf<Offset>()
        for (i in 0 until numBands) {
            val gain = bandGains.getOrElse(i) { 0f }
            val x = (i.toFloat() / (numBands - 1).toFloat()) * cW
            val y = midY - (gain / 12f) * (midY - 6.dp.toPx())
            points.add(Offset(x, y.coerceIn(4f, cH - 4f)))
        }

        // Build smooth cubic bezier curve
        val strokePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        // Build filled gradient path underneath
        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(cW, midY)
            lineTo(0f, midY)
            close()
        }

        // Draw soft gradient area towards 0 dB
        if (isEnabled) {
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.25f),
                        primaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
        }

        // Draw continuous stroke curve
        drawPath(
            path = strokePath,
            color = curveColor,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Draw small dot on each band node
        points.forEach { pt ->
            drawCircle(
                color = curveColor,
                radius = 2.5.dp.toPx(),
                center = pt
            )
        }
    }
}
