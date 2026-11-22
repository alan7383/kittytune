@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.getSettingsShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.audio.haptics.performAppHaptic
import android.view.HapticFeedbackConstants

/** What the full effect list shows: everything, what is on, what the grid holds, or one category. */
internal object AudioFxFilter {
    const val ALL = "all"
    const val ACTIVE = "active"
    const val PINNED = "pinned"
    fun category(categoryRes: Int) = "category:$categoryRes"
}

private data class AudioFxCategory(val titleRes: Int, val shortRes: Int, val icon: ImageVector)

private val audioFxCategories = listOf(
    AudioFxCategory(R.string.category_power_eq, R.string.fx_cat_power, Icons.Rounded.Bolt),
    AudioFxCategory(R.string.category_spatial, R.string.fx_cat_spatial, Icons.Rounded.SurroundSound),
    AudioFxCategory(R.string.category_ambience_filters, R.string.fx_cat_ambience, Icons.Rounded.WaterDrop),
    AudioFxCategory(R.string.category_retro_vintage, R.string.fx_cat_retro, Icons.Rounded.Radio),
)

/**
 * Saving the current sound and opening the full effect list, as one connected Material 3 button group:
 * round outside, tight between the two, each morphing as it is pressed.
 */
@Composable
internal fun AudioFxActionsRow(onSave: () -> Unit, onExplore: () -> Unit, modifier: Modifier = Modifier) {
    val leading = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val trailing = ButtonGroupDefaults.connectedTrailingButtonShapes()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        FilledTonalButton(
            onClick = hapticAction(HapticKind.UI, onSave),
            shapes = ButtonShapes(leading.shape, leading.pressedShape),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 60.dp).testTag("audio-fx-save-preset"),
        ) {
            Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.audio_fx_save_preset), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
        }
        FilledTonalButton(
            onClick = hapticAction(HapticKind.UI, onExplore),
            shapes = ButtonShapes(trailing.shape, trailing.pressedShape),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 60.dp).testTag("audio-fx-explore"),
        ) {
            Icon(Icons.Rounded.DashboardCustomize, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_explore_all_effects), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Every effect, to turn on and off where it is, pin to the grid or adjust; the grid itself sits on top,
 * where its tiles are dragged into order. [effects] holds the effects only, not the saved presets.
 */
@Composable
internal fun AudioFxExplorer(
    viewModel: PlayerViewModel,
    effects: List<AudioFxDefinition>,
    initialFilter: String,
    onToggle: (AudioFxDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var isDraggingTile by remember { mutableStateOf(false) }
    val state = viewModel.effectsState
    val pinned = viewModel.pinnedAudioFx
    val activeCount = effects.count { it.isActive(state) }
    val descriptions = effects.associate { it.id to audioFxDescription(it.id) }
    val titles = effects.associate { it.id to stringResource(it.titleRes) }
    val needle = query.trim()
    val shown = effects.filter { fx ->
        val matchesFilter = when (filter) {
            AudioFxFilter.ALL -> true
            AudioFxFilter.ACTIVE -> fx.isActive(state)
            AudioFxFilter.PINNED -> fx.id in pinned
            else -> AudioFxFilter.category(fx.categoryRes) == filter
        }
        matchesFilter && (needle.isEmpty() ||
            titles[fx.id].orEmpty().contains(needle, ignoreCase = true) ||
            descriptions[fx.id].orEmpty().contains(needle, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState(), enabled = !isDraggingTile)
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.btn_back))
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(activeCount > 0, enter = fadeIn(), exit = fadeOut()) {
                TextButton(
                    onClick = hapticAction(HapticKind.MEDIUM) { viewModel.resetAllEffects() },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.fx_turn_all_off))
                }
            }
        }
        Text(
            stringResource(R.string.fx_explore_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            stringResource(R.string.fx_explore_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().testTag("audio-fx-search"),
            placeholder = { Text(stringResource(R.string.fx_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { query = "" }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_clear))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(12.dp))

        // Two connected groups: what to show, then which family of effects.
        val scopeFilters = listOf(AudioFxFilter.ALL, AudioFxFilter.ACTIVE, AudioFxFilter.PINNED)
        ExpressiveConnectedButtonGroup(
            options = scopeFilters,
            selectedOption = filter.takeIf { it in scopeFilters },
            onOptionSelected = { filter = it },
            fillMaxWidth = true,
            uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            labelProvider = { id ->
                Text(
                    when (id) {
                        AudioFxFilter.ALL -> stringResource(R.string.fx_filter_all)
                        AudioFxFilter.ACTIVE -> stringResource(R.string.fx_filter_active, activeCount)
                        else -> stringResource(R.string.fx_filter_pinned, pinned.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("audio-fx-filter-$id"),
                )
            },
        )
        ExpressiveConnectedButtonGroup(
            options = audioFxCategories,
            selectedOption = audioFxCategories.firstOrNull { AudioFxFilter.category(it.titleRes) == filter },
            onOptionSelected = { category ->
                val id = AudioFxFilter.category(category.titleRes)
                filter = if (filter == id) AudioFxFilter.ALL else id
            },
            fillMaxWidth = true,
            uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            labelProvider = { category ->
                Text(
                    stringResource(category.shortRes),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        Spacer(Modifier.height(20.dp))

        if (filter == AudioFxFilter.ALL && needle.isEmpty()) {
            AudioFxSectionHeader(
                title = stringResource(R.string.fx_your_grid),
                subtitle = stringResource(R.string.fx_your_grid_hint),
                icon = Icons.Rounded.DashboardCustomize,
                action = {
                    TextButton(
                        onClick = hapticAction(HapticKind.MEDIUM) { viewModel.resetPinnedAudioFx() },
                        shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_reset))
                    }
                },
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(12.dp)) {
                    DraggablePinnedTilesGrid(
                        pinnedList = pinned,
                        allEffects = effects,
                        viewModel = viewModel,
                        onDragStateChanged = { isDraggingTile = it },
                        onRemoveFx = { fxId ->
                            view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                            viewModel.togglePinAudioFx(fxId)
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (shown.isEmpty()) {
            AudioFxEmptyState(
                text = when {
                    needle.isNotEmpty() -> stringResource(R.string.fx_no_results, needle)
                    filter == AudioFxFilter.ACTIVE -> stringResource(R.string.fx_none_active)
                    else -> stringResource(R.string.edit_tiles_subtitle)
                },
            )
        } else {
            audioFxCategories.forEach { category ->
                val inCategory = shown.filter { it.categoryRes == category.titleRes }
                if (inCategory.isEmpty()) return@forEach
                AudioFxSectionHeader(title = stringResource(category.titleRes), icon = category.icon)
                AudioFxGroup {
                    inCategory.forEachIndexed { index, fx ->
                        AudioFxRow(
                            fx = fx,
                            shape = getSettingsShape(inCategory.size, index),
                            title = titles[fx.id].orEmpty(),
                            description = descriptions[fx.id],
                            isActive = fx.isActive(state),
                            isPinned = fx.id in pinned,
                            onToggle = { onToggle(fx) },
                            onTogglePin = { viewModel.togglePinAudioFx(fx.id) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AudioFxSectionHeader(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        action?.invoke()
    }
}

/** Rows joined into one rounded block, with a hairline of the background between them. */
@Composable
private fun AudioFxGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun AudioFxRow(
    fx: AudioFxDefinition,
    shape: androidx.compose.ui.graphics.Shape,
    title: String,
    description: String?,
    isActive: Boolean,
    isPinned: Boolean,
    onToggle: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val danger = fx.id == "earrape"
    val accent = if (danger) scheme.error else scheme.primary
    val onAccent = if (danger) scheme.onError else scheme.onPrimary
    val container by animateColorAsState(
        when {
            isActive && danger -> scheme.errorContainer
            isActive -> scheme.secondaryContainer
            else -> scheme.surfaceContainerHigh
        },
        label = "fxRowContainer",
    )
    val content by animateColorAsState(
        when {
            isActive && danger -> scheme.onErrorContainer
            isActive -> scheme.onSecondaryContainer
            else -> scheme.onSurface
        },
        label = "fxRowContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "fxIconScale",
    )
    val toggle = hapticAction(HapticKind.MEDIUM, onToggle)
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = toggle,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
        ),
        shape = shape,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().testTag("audio-fx-row-${fx.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(if (isActive) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                    .background(if (isActive) accent else scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    fx.icon,
                    contentDescription = null,
                    tint = if (isActive) onAccent else scheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) content.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val pin = hapticAction(HapticKind.LIGHT, onTogglePin)
            IconButton(
                onClick = pin,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(40.dp).testTag("audio-fx-pin-${fx.id}"),
            ) {
                Icon(
                    if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(if (isPinned) R.string.fx_unpin else R.string.fx_pin),
                    tint = if (isPinned) accent else content.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            val settings = fx.onOpenDialog
            if (settings != null) {
                IconButton(
                    onClick = hapticAction(HapticKind.LIGHT, settings),
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(40.dp).testTag("audio-fx-settings-${fx.id}"),
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.fx_settings),
                        tint = content.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                // Keeps the pin in line with the rows that have settings.
                Spacer(Modifier.width(40.dp))
            }
            Spacer(Modifier.width(4.dp))
            SettingsSwitch(
                checked = isActive,
                onCheckedChange = { toggle() },
                interactionSource = interactionSource,
                colors = if (danger) SwitchDefaults.colors(
                    checkedTrackColor = scheme.error,
                    checkedThumbColor = scheme.onError,
                    checkedIconColor = scheme.error,
                ) else null,
                checkedIconTint = if (danger) scheme.error else scheme.primary,
                modifier = Modifier.testTag("audio-fx-switch-${fx.id}"),
            )
        }
    }
}

@Composable
private fun AudioFxEmptyState(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The one-line description of an effect (`effect_tip_<id>`), when it has one. */
@Composable
private fun audioFxDescription(id: String): String? {
    val context = LocalContext.current
    val res = remember(id) { context.resources.getIdentifier("effect_tip_$id", "string", context.packageName) }
    return if (res != 0) stringResource(res) else null
}
