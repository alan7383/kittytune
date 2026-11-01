package com.alananasss.kittytune.ui.profile

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.login.SetupBottomBar
import com.alananasss.kittytune.ui.profile.pet.IvyUnlockScreen
import com.alananasss.kittytune.ui.profile.pet.PetCoatEditor
import com.alananasss.kittytune.ui.profile.pet.PetPhotoScreen
import com.alananasss.kittytune.ui.profile.pet.PetSprite
import com.alananasss.kittytune.ui.theme.GoogleSansRounded
import com.alananasss.kittytune.ui.widget.PetWidget
import com.alananasss.kittytune.ui.widget.PetWidgetReceiver
import com.alananasss.kittytune.ui.widget.pet.PetActivity
import com.alananasss.kittytune.ui.widget.pet.PetBrain
import com.alananasss.kittytune.ui.widget.pet.PetCoat
import com.alananasss.kittytune.ui.widget.pet.PetColor
import com.alananasss.kittytune.ui.widget.pet.PetConfig
import com.alananasss.kittytune.ui.widget.pet.PetEye
import com.alananasss.kittytune.ui.widget.pet.PetHat
import com.alananasss.kittytune.ui.widget.pet.PetIvy
import com.alananasss.kittytune.ui.widget.pet.PetNeck
import com.alananasss.kittytune.ui.widget.pet.PetPattern
import com.alananasss.kittytune.ui.widget.pet.PetPersonality
import com.alananasss.kittytune.ui.widget.pet.PetSettings
import com.alananasss.kittytune.ui.widget.pet.PetSpeed
import com.alananasss.kittytune.ui.widget.pet.PetTap
import com.alananasss.kittytune.ui.widget.pet.PetWall
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class PetScreenMode { SETUP, SETTINGS, PHOTO, EDITOR, IVY }

/**
 * Kitty pet settings. The first visit runs a small setup (meet, look, name, how it works, add the
 * widget); afterwards it is a full settings page: look, accessories, room, widget, behaviour, the
 * rare-moments collection and an experimental "make it from a photo" mode.
 */
@Composable
fun PetSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(PetConfig.load(context)) }
    var mode by remember { mutableStateOf(if (PetConfig.isOnboarded(context)) PetScreenMode.SETTINGS else PetScreenMode.SETUP) }
    var returnTo by remember { mutableStateOf(PetScreenMode.SETTINGS) }
    var ivyReturn by remember { mutableStateOf(PetScreenMode.SETTINGS) }

    fun revealIvy() {
        PetConfig.setIvyUnlocked(context)
        ivyReturn = if (mode == PetScreenMode.IVY) ivyReturn else mode
        mode = PetScreenMode.IVY
    }

    fun update(value: PetSettings) {
        val renamedIvy = PetIvy.isIvyName(value.name) && !PetIvy.isIvyName(settings.name)
        settings = value
        PetConfig.save(context, value)
        PetWidget.refresh(context, reroll = false)
        // easter egg: naming the cat "Ivy"
        if (renamedIvy && !PetConfig.isIvyUnlocked(context)) revealIvy()
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "petScreen"
    ) { m ->
        when (m) {
            PetScreenMode.SETUP -> PetOnboarding(
                settings = settings,
                onChange = ::update,
                onPhoto = { returnTo = PetScreenMode.SETUP; mode = PetScreenMode.PHOTO },
                onBack = { if (PetConfig.isOnboarded(context)) mode = PetScreenMode.SETTINGS else onBackClick() },
                onFinish = {
                    PetConfig.setOnboarded(context, true)
                    PetWidget.refresh(context)
                    mode = PetScreenMode.SETTINGS
                }
            )

            PetScreenMode.SETTINGS -> PetSettingsContent(
                settings = settings,
                onChange = ::update,
                onBackClick = onBackClick,
                onReplayTutorial = { mode = PetScreenMode.SETUP },
                onPhoto = { returnTo = PetScreenMode.SETTINGS; mode = PetScreenMode.PHOTO },
                onEditCoat = { mode = PetScreenMode.EDITOR },
                onSecretBoops = { if (!PetConfig.isIvyUnlocked(context)) revealIvy() },
                onShowIvy = { revealIvy() },
                onReset = {
                    PetConfig.reset(context)
                    settings = PetConfig.load(context)
                    PetWidget.refresh(context)
                }
            )

            PetScreenMode.IVY -> IvyUnlockScreen(
                settings = settings,
                onAdopt = {
                    update(PetIvy.apply(settings))
                    mode = ivyReturn
                },
                onClose = { mode = ivyReturn }
            )

            PetScreenMode.EDITOR -> PetCoatEditor(
                settings = settings,
                initial = settings.coat?.takeIf { settings.pattern == PetPattern.CUSTOM }
                    ?: PetCoat.fromPreset(if (settings.pattern == PetPattern.CUSTOM) PetPattern.CLASSIC else settings.pattern),
                onSave = {
                    update(settings.copy(pattern = PetPattern.CUSTOM, coat = it))
                    mode = PetScreenMode.SETTINGS
                },
                onClose = { mode = PetScreenMode.SETTINGS }
            )

            PetScreenMode.PHOTO -> PetPhotoScreen(
                settings = settings,
                onApply = {
                    update(it)
                    Toast.makeText(context, context.getString(R.string.pet_photo_applied, it.name), Toast.LENGTH_SHORT).show()
                    mode = returnTo
                },
                onClose = { mode = returnTo }
            )
        }
    }
}

// ------------------------------------------------------------------ settings page

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PetSettingsContent(
    settings: PetSettings,
    onChange: (PetSettings) -> Unit,
    onBackClick: () -> Unit,
    onReplayTutorial: () -> Unit,
    onPhoto: () -> Unit,
    onEditCoat: () -> Unit,
    onSecretBoops: () -> Unit,
    onShowIvy: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    var editingName by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var boops by remember { mutableIntStateOf(PetConfig.boops(context)) }
    val recentBoops = remember { ArrayDeque<Long>() }
    val ivyUnlocked = remember(settings) { PetConfig.isIvyUnlocked(context) }
    val raresSeen = remember(settings) { PetConfig.raresSeen(context) }
    val rares = PetActivity.rares

    SettingsScaffold(
        title = stringResource(R.string.pet_settings_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp)
        ) {
            // ---------------- hero
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // dances (on the real beat) while music plays, otherwise shows off its routine
                    val playingTrack by androidx.compose.runtime.produceState<Long?>(null) {
                        while (true) {
                            value = runCatching {
                                val mm = com.alananasss.kittytune.data.MusicManager
                                if (mm.player.isPlaying) mm.currentTrack?.id ?: -1L else null
                            }.getOrNull()
                            delay(1_000)
                        }
                    }
                    val cycling = rememberCyclingActivity()
                    PetSprite(
                        activity = playingTrack?.let { PetBrain.danceFor(it) } ?: cycling,
                        settings = settings,
                        modifier = Modifier.fillMaxWidth(),
                        boopable = true,
                        onBoop = {
                            PetConfig.addBoop(context); boops++
                            // easter egg: 10 boops in a row
                            val now = System.currentTimeMillis()
                            recentBoops.addLast(now)
                            while (recentBoops.isNotEmpty() && now - recentBoops.first() > PetIvy.BOOP_WINDOW_MS) recentBoops.removeFirst()
                            if (recentBoops.size >= PetIvy.BOOPS_TO_UNLOCK) { recentBoops.clear(); onSecretBoops() }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = settings.name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.pet_settings_tap_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill(Icons.Rounded.CalendarMonth, stringResource(R.string.pet_stats_days, PetBrain.daysTogether(context)))
                        StatPill(Icons.Rounded.Favorite, stringResource(R.string.pet_stats_boops, boops))
                        StatPill(Icons.Rounded.AutoAwesome, "${raresSeen.size}/${rares.size}")
                    }
                    if (ivyUnlocked) {
                        Spacer(Modifier.height(12.dp))
                        IvyModeChip(
                            on = settings.ivyMode,
                            onToggle = { on -> onChange(if (on) PetIvy.apply(settings) else settings.copy(ivyMode = false)) },
                            onReplay = onShowIvy
                        )
                    }
                }
            }

            // ---------------- look
            item { SectionTitle(stringResource(R.string.pet_section_look)) }
            item {
                SettingsGroup(
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_name),
                                subtitle = settings.name,
                                icon = Icons.Rounded.Badge,
                                onClick = { editingName = true }
                            )
                        },
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_color), colorLabel(settings)) {
                                ColorSwatches(settings = settings, onSelect = { onChange(settings.copy(colorId = it)) })
                            }
                        },
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_pattern), stringResource(settings.pattern.label)) {
                                PreviewRow(patternOptions(settings), settings.pattern, { stringResource(it.label) }, { settings.copy(pattern = it) }) {
                                    onChange(settings.copy(pattern = it))
                                }
                                Spacer(Modifier.height(12.dp))
                                FilledTonalButton(onClick = onEditCoat, shapes = ButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.Brush, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.pet_editor_open))
                                }
                            }
                        },
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_eyes), eyeLabel(settings.eyeColor)) {
                                EyeSwatches(settings.eyeColor) { onChange(settings.copy(eyeColor = it)) }
                            }
                        },
                    )
                )
            }

            // ---------------- photo (experimental)
            item {
                Surface(
                    onClick = onPhoto,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.pet_photo_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiary) {
                                    Text(
                                        stringResource(R.string.pet_experimental),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.pet_photo_card_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // ---------------- accessories
            item { SectionTitle(stringResource(R.string.pet_section_accessories)) }
            item {
                SettingsGroup(
                    items = listOf(
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_hat), stringResource(settings.hat.label)) {
                                PreviewRow(PetHat.entries, settings.hat, { stringResource(it.label) }, { settings.copy(hat = it) }) {
                                    onChange(settings.copy(hat = it))
                                }
                            }
                        },
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_neck), stringResource(settings.neck.label)) {
                                PreviewRow(PetNeck.entries, settings.neck, { stringResource(it.label) }, { settings.copy(neck = it) }) {
                                    onChange(settings.copy(neck = it))
                                }
                            }
                        },
                    )
                )
            }

            // ---------------- room
            item { SectionTitle(stringResource(R.string.pet_section_room)) }
            item {
                SettingsGroup(
                    items = listOf(
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_wall), stringResource(settings.wall.label)) {
                                WallChips(settings.wall) { onChange(settings.copy(wall = it)) }
                            }
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_room_decor),
                                subtitle = stringResource(R.string.pet_settings_room_decor_sub),
                                icon = Icons.Rounded.Chair,
                                hasSwitch = true,
                                switchState = settings.roomDecor,
                                onSwitchChange = { onChange(settings.copy(roomDecor = it)) },
                                onClick = { onChange(settings.copy(roomDecor = !settings.roomDecor)) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_holidays),
                                subtitle = stringResource(R.string.pet_settings_holidays_sub),
                                icon = Icons.Rounded.Cake,
                                hasSwitch = true,
                                switchState = settings.holidays,
                                onSwitchChange = { onChange(settings.copy(holidays = it)) },
                                onClick = { onChange(settings.copy(holidays = !settings.holidays)) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_seasonal),
                                subtitle = stringResource(R.string.pet_settings_seasonal_sub),
                                icon = Icons.Rounded.AcUnit,
                                hasSwitch = true,
                                switchState = settings.seasonal,
                                onSwitchChange = { onChange(settings.copy(seasonal = it)) },
                                onClick = { onChange(settings.copy(seasonal = !settings.seasonal)) }
                            )
                        },
                    )
                )
            }

            // ---------------- widget
            item { SectionTitle(stringResource(R.string.pet_settings_widget_section)) }
            item {
                SettingsGroup(
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_show_name),
                                icon = Icons.Rounded.Label,
                                hasSwitch = true,
                                switchState = settings.showName,
                                onSwitchChange = { onChange(settings.copy(showName = it)) },
                                onClick = { onChange(settings.copy(showName = !settings.showName)) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_show_hearts),
                                icon = Icons.Rounded.Favorite,
                                hasSwitch = true,
                                switchState = settings.showHearts,
                                onSwitchChange = { onChange(settings.copy(showHearts = it)) },
                                onClick = { onChange(settings.copy(showHearts = !settings.showHearts)) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_show_status),
                                icon = Icons.Rounded.ChatBubble,
                                hasSwitch = true,
                                switchState = settings.showStatus,
                                onSwitchChange = { onChange(settings.copy(showStatus = it)) },
                                onClick = { onChange(settings.copy(showStatus = !settings.showStatus)) }
                            )
                        },
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_tap), null) {
                                ExpressiveConnectedButtonGroup(
                                    options = PetTap.entries,
                                    selectedOption = settings.tap,
                                    onOptionSelected = { onChange(settings.copy(tap = it)) },
                                    labelProvider = { Text(stringResource(it.label), maxLines = 1) }
                                )
                            }
                        },
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_speed), null) {
                                ExpressiveConnectedButtonGroup(
                                    options = PetSpeed.entries,
                                    selectedOption = settings.speed,
                                    onOptionSelected = { onChange(settings.copy(speed = it)) },
                                    labelProvider = { Text(stringResource(it.label), maxLines = 1) }
                                )
                            }
                        },
                    )
                )
            }

            // ---------------- behaviour
            item { SectionTitle(stringResource(R.string.pet_section_behaviour)) }
            item {
                SettingsGroup(
                    items = listOf(
                        { shape ->
                            OptionBlock(shape, stringResource(R.string.pet_settings_personality), stringResource(settings.personality.label)) {
                                PersonalityChips(settings.personality) { onChange(settings.copy(personality = it)) }
                            }
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_sad_after),
                                subtitle = stringResource(R.string.pet_hours_format, fmtHours(settings.sadHours)),
                                icon = Icons.Rounded.SentimentDissatisfied,
                                hasSlider = true,
                                sliderValue = settings.sadHours,
                                sliderRange = 0.5f..12f,
                                onSliderChange = {
                                    val v = (it * 2).roundToInt() / 2f
                                    onChange(settings.copy(sadHours = v, sleepHours = maxOf(settings.sleepHours, v + 1f)))
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_sleep_after),
                                subtitle = stringResource(R.string.pet_hours_format, fmtHours(settings.sleepHours)),
                                icon = Icons.Rounded.Bedtime,
                                hasSlider = true,
                                sliderValue = settings.sleepHours,
                                sliderRange = 1f..48f,
                                onSliderChange = {
                                    val v = it.roundToInt().toFloat().coerceAtLeast(settings.sadHours + 0.5f)
                                    onChange(settings.copy(sleepHours = v))
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_night_sleep),
                                subtitle = stringResource(R.string.pet_settings_night_sleep_sub),
                                icon = Icons.Rounded.Nightlight,
                                hasSwitch = true,
                                switchState = settings.nightSleep,
                                onSwitchChange = { onChange(settings.copy(nightSleep = it)) },
                                onClick = { onChange(settings.copy(nightSleep = !settings.nightSleep)) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_events),
                                subtitle = stringResource(R.string.pet_settings_events_sub),
                                icon = Icons.Rounded.Event,
                                hasSwitch = true,
                                switchState = settings.events,
                                onSwitchChange = { onChange(settings.copy(events = it)) },
                                onClick = { onChange(settings.copy(events = !settings.events)) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_exclusives),
                                subtitle = stringResource(R.string.pet_settings_exclusives_sub),
                                icon = Icons.Rounded.LibraryMusic,
                                hasSwitch = true,
                                switchState = settings.exclusives,
                                onSwitchChange = { onChange(settings.copy(exclusives = it)) },
                                onClick = { onChange(settings.copy(exclusives = !settings.exclusives)) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_rares),
                                subtitle = stringResource(R.string.pet_settings_rares_sub),
                                icon = Icons.Rounded.Diamond,
                                hasSwitch = true,
                                switchState = settings.rares,
                                onSwitchChange = { onChange(settings.copy(rares = it)) },
                                onClick = { onChange(settings.copy(rares = !settings.rares)) }
                            )
                        },
                    )
                )
            }

            // ---------------- collection
            item { SectionTitle(stringResource(R.string.pet_section_collection)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(R.string.pet_rare_tip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.pet_collection_count, raresSeen.size, rares.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    RareGrid(rares, raresSeen, settings)
                }
            }

            // ---------------- actions
            item { SectionTitle(stringResource(R.string.pet_section_more)) }
            item {
                SettingsGroup(
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_add_widget),
                                subtitle = stringResource(R.string.pet_ob_widget_body),
                                icon = Icons.Rounded.Add,
                                onClick = { requestPinPetWidget(context) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_replay),
                                subtitle = stringResource(R.string.pet_settings_replay_sub),
                                icon = Icons.Rounded.Replay,
                                onClick = onReplayTutorial
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pet_settings_reset),
                                subtitle = stringResource(R.string.pet_settings_reset_sub),
                                icon = Icons.Rounded.RestartAlt,
                                onClick = { confirmReset = true }
                            )
                        },
                    )
                )
            }
        }
    }

    if (editingName) {
        NameDialog(
            initial = settings.name,
            onDismiss = { editingName = false },
            onConfirm = {
                onChange(settings.copy(name = it.ifBlank { PetConfig.DEFAULT_NAME }))
                editingName = false
            }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.pet_settings_reset)) },
            text = { Text(stringResource(R.string.pet_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset(); boops = 0 }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.pet_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.pet_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Box(Modifier.padding(horizontal = 16.dp)) { SettingsGroupTitle(text) }
}

@Composable
private fun StatPill(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Patterns offered: "Custom" only once a pixel coat exists. */
private fun patternOptions(settings: PetSettings): List<PetPattern> =
    PetPattern.entries.filter { it != PetPattern.CUSTOM || settings.coat != null }

private fun personalityIcon(p: PetPersonality): ImageVector = when (p) {
    PetPersonality.BALANCED -> Icons.Rounded.Balance
    PetPersonality.LAZY -> Icons.Rounded.Bedtime
    PetPersonality.PLAYFUL -> Icons.Rounded.SportsBaseball
    PetPersonality.FOODIE -> Icons.Rounded.SetMeal
    PetPersonality.CURIOUS -> Icons.Rounded.TravelExplore
    PetPersonality.CUDDLY -> Icons.Rounded.Favorite
}

/** A settings card with a title, the current value and a custom picker below. */
@Composable
private fun OptionBlock(
    shape: androidx.compose.ui.graphics.Shape,
    title: String,
    value: String?,
    content: @Composable () -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Horizontal row of still previews of the cat wearing / being each option. */
@Composable
private fun <T> PreviewRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    look: (T) -> PetSettings,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options) { option ->
            val isSelected = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Column(Modifier.padding(6.dp).width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // zoomed on the cat, without the room, so small details (hats, collars…) read well
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(46f / 32f)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        PetSprite(
                            PetActivity.IDLE, look(option).copy(roomDecor = false),
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = 1.25f; scaleY = 1.25f
                                    translationY = -size.height * 0.02f
                                },
                            corner = 0, animate = false
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun colorLabel(settings: PetSettings): String =
    if (settings.colorId == PetColor.CUSTOM && settings.customPalette == PetIvy.palette) PetIvy.NAME
    else if (settings.colorId == PetColor.CUSTOM) stringResource(R.string.pet_color_custom)
    else stringResource((PetColor.fromId(settings.colorId) ?: PetColor.CREAM).label)

@Composable
private fun eyeLabel(color: Int): String =
    if (color == PetIvy.EYES) PetIvy.NAME else PetEye.entries.firstOrNull { it.color == color }?.let { stringResource(it.label) } ?: stringResource(R.string.pet_color_custom)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(settings: PetSettings, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PetColor.entries.forEach { c ->
            Swatch(fill = Color(c.palette.fur), ring = Color(c.palette.mark), selected = settings.colorId == c.id) { onSelect(c.id) }
        }
        settings.customPalette?.let { p ->
            Swatch(fill = Color(p.fur), ring = Color(p.mark), selected = settings.colorId == PetColor.CUSTOM, badge = Icons.Rounded.PhotoCamera) {
                onSelect(PetColor.CUSTOM)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EyeSwatches(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PetEye.entries.forEach { e ->
            Swatch(fill = Color(e.color), ring = Color(0xFF3D2B3D), selected = selected == e.color) { onSelect(e.color) }
        }
        if (PetEye.entries.none { it.color == selected }) {
            Swatch(fill = Color(selected), ring = Color(0xFF3D2B3D), selected = true, badge = Icons.Rounded.PhotoCamera) {}
        }
    }
}

@Composable
private fun Swatch(fill: Color, ring: Color, selected: Boolean, badge: ImageVector? = null, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "swatch")
    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(if (selected) 3.dp else 2.dp, if (selected) MaterialTheme.colorScheme.primary else ring), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF3D2B3D), modifier = Modifier.size(20.dp))
            badge != null -> Icon(badge, contentDescription = null, tint = Color(0xFF3D2B3D), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallChips(selected: PetWall, onSelect: (PetWall) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PetWall.entries.forEach { w ->
            FilterChip(
                selected = w == selected,
                onClick = { onSelect(w) },
                label = { Text(stringResource(w.label)) },
                leadingIcon = {
                    val c = w.color?.let { Color(it) }
                        ?: if (w == PetWall.MATERIAL) MaterialTheme.colorScheme.secondaryContainer else Color(0xFFE8F5EC)
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalityChips(selected: PetPersonality, onSelect: (PetPersonality) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PetPersonality.entries.forEach { p ->
            FilterChip(
                selected = p == selected,
                onClick = { onSelect(p) },
                label = { Text(stringResource(p.label)) },
                leadingIcon = { Icon(personalityIcon(p), null, Modifier.size(18.dp)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RareGrid(rares: List<PetActivity>, seen: Set<String>, settings: PetSettings) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2,
        modifier = Modifier.fillMaxWidth()
    ) {
        rares.forEach { rare ->
            val found = rare.name in seen
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (found) {
                        PetSprite(rare, settings, Modifier.fillMaxWidth(), corner = 14)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(46f / 32f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (found) stringResource(rare.status) else stringResource(R.string.pet_collection_locked),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun fmtHours(h: Float): String = if (h % 1f == 0f) h.toInt().toString() else String.format("%.1f", h)

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pet_settings_name)) },
        text = { NameField(value = value, onValueChange = { value = it }) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.pet_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.pet_cancel)) } }
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(16)) },
        singleLine = true,
        label = { Text(stringResource(R.string.pet_ob_name_hint)) },
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            IconButton(
                onClick = { onValueChange(PetConfig.NAME_IDEAS.filter { it != value }.random()) },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = stringResource(R.string.pet_ob_name_random))
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

// ------------------------------------------------------------------ onboarding

private enum class PetSetupPage { HELLO, LOOK, NAME, HOW, WIDGET }

@Composable
private fun PetOnboarding(
    settings: PetSettings,
    onChange: (PetSettings) -> Unit,
    onPhoto: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val pages = PetSetupPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var draftName by remember { mutableStateOf(settings.name) }

    BackHandler {
        if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } else onBack()
    }

    Scaffold(
        bottomBar = {
            SetupBottomBar(
                pagerState = pagerState,
                onNextClicked = {
                    if (pages[pagerState.currentPage] == PetSetupPage.NAME) onChange(settings.copy(name = draftName.ifBlank { PetConfig.DEFAULT_NAME }))
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                onBackClicked = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                onFinishClicked = {
                    onChange(settings.copy(name = draftName.ifBlank { PetConfig.DEFAULT_NAME }))
                    onFinish()
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { index ->
            when (pages[index]) {
                PetSetupPage.HELLO -> SetupPage(
                    title = stringResource(R.string.pet_ob_hello_title),
                    body = stringResource(R.string.pet_ob_hello_body),
                    sprite = { PetSprite(PetActivity.DANCE_BOB, settings, Modifier.fillMaxWidth(), boopable = true) }
                )

                PetSetupPage.LOOK -> SetupPage(
                    title = stringResource(R.string.pet_ob_color_title),
                    body = stringResource(R.string.pet_ob_color_body),
                    sprite = { PetSprite(PetActivity.IDLE, settings, Modifier.fillMaxWidth(), boopable = true) }
                ) {
                    Text(colorLabel(settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    ColorSwatches(settings) { onChange(settings.copy(colorId = it)) }
                    Spacer(Modifier.height(16.dp))
                    PreviewRow(patternOptions(settings), settings.pattern, { stringResource(it.label) }, { settings.copy(pattern = it) }) {
                        onChange(settings.copy(pattern = it))
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onPhoto, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pet_ob_photo))
                    }
                }

                PetSetupPage.NAME -> SetupPage(
                    title = stringResource(R.string.pet_ob_name_title),
                    body = stringResource(R.string.pet_ob_name_body),
                    sprite = { PetSprite(PetActivity.WAVE, settings, Modifier.fillMaxWidth(), boopable = true, label = draftName) }
                ) {
                    NameField(value = draftName, onValueChange = { draftName = it })
                }

                PetSetupPage.HOW -> HowItWorksPage(settings)

                PetSetupPage.WIDGET -> {
                    val context = LocalContext.current
                    SetupPage(
                        title = stringResource(R.string.pet_ob_widget_title),
                        body = stringResource(R.string.pet_ob_widget_body),
                        sprite = { PetSprite(PetActivity.LOVE, settings, Modifier.fillMaxWidth(), label = draftName) }
                    ) {
                        Button(onClick = { requestPinPetWidget(context) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pet_add_widget))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupPage(
    title: String,
    body: String,
    sprite: @Composable () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.widthIn(max = 420.dp)) { sprite() }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = GoogleSansRounded, fontSize = 30.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
            extra()
        }
    }
}

@Composable
private fun HowItWorksPage(settings: PetSettings) {
    val rows = listOf(
        rememberCyclingActivity(PetActivity.DANCES) to R.string.pet_how_music,
        rememberCyclingActivity() to R.string.pet_how_day,
        rememberCyclingActivity(PetActivity.SAD_MOODS) to R.string.pet_how_sad,
        PetActivity.SLEEP to R.string.pet_how_sleep,
        PetActivity.BOOP to R.string.pet_how_boop,
        PetActivity.LOVE to R.string.pet_how_rare,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.pet_ob_how_title),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = GoogleSansRounded, fontSize = 30.sp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        rows.forEach { (activity, text) ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(vertical = 6.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(120.dp)) {
                        PetSprite(activity, settings, Modifier.fillMaxWidth(), corner = 16)
                        if (text == R.string.pet_how_rare) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiary)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(text),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Rotates through activities so a preview shows off what the cat can do. */
@Composable
private fun rememberCyclingActivity(
    showcase: List<PetActivity> = listOf(
        PetActivity.WAVE, PetActivity.EAT, PetActivity.PLAY, PetActivity.BUTTERFLY, PetActivity.LASER, PetActivity.BOX,
        PetActivity.BUBBLES, PetActivity.KNEAD, PetActivity.MOUSE, PetActivity.BIRD, PetActivity.LOAF, PetActivity.TREAT,
        PetActivity.GROOM, PetActivity.HUM, PetActivity.SNEEZE, PetActivity.GIFT, PetActivity.DOZE,
        PetActivity.DIG, PetActivity.JUDGE, PetActivity.FAN, PetActivity.DANCE_GUITAR, PetActivity.DANCE_DJ,
    ),
): PetActivity {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(showcase) {
        while (true) {
            delay(4_000)
            index = (index + 1) % showcase.size
        }
    }
    return showcase[index % showcase.size]
}

private fun requestPinPetWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, PetWidgetReceiver::class.java)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(context, R.string.pet_add_widget_manual, Toast.LENGTH_LONG).show()
    }
}


private val IVY_GLOW = Color(0xFF8FE3FF)

/**
 * Ivy mode switch, once the easter egg is found: a pill that turns into a starry sky when on, with
 * a little round "see the reveal again" button glued to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IvyModeChip(on: Boolean, onToggle: (Boolean) -> Unit, onReplay: () -> Unit) {
    val space = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF060A18), Color(0xFF0E2A3F), Color(0xFF1B3B5C)))
    val scale by animateFloatAsState(if (on) 1.04f else 1f, spring(dampingRatio = 0.45f), label = "ivyChip")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = { onToggle(!on) },
            shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, if (on) IVY_GLOW else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.scale(scale)
        ) {
            Row(
                Modifier
                    .then(if (on) Modifier.background(space) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    .padding(start = 14.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = if (on) IVY_GLOW else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.pet_ivy_mode),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (on) IVY_GLOW else MaterialTheme.colorScheme.outline)
                )
            }
        }
        Surface(
            onClick = onReplay,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 50.dp, bottomEnd = 50.dp),
            color = if (on) Color(0xFF0E2A3F) else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, if (on) IVY_GLOW else MaterialTheme.colorScheme.outlineVariant),
        ) {
            Icon(
                Icons.Rounded.Replay,
                contentDescription = stringResource(R.string.pet_ivy_replay),
                tint = if (on) IVY_GLOW else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .size(18.dp)
            )
        }
    }
}
