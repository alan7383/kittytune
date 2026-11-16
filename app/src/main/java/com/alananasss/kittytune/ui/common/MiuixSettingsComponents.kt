package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.Icon as M3Icon
import top.yukonga.miuix.kmp.basic.Card as MxCard
import top.yukonga.miuix.kmp.basic.IconButton as MxIconButton
import top.yukonga.miuix.kmp.basic.Slider as MxSlider
import top.yukonga.miuix.kmp.basic.TopAppBar as MxTopAppBar

/** Inside a MIUIX settings card: rows are flat and share the card. */
val LocalMiuixSettingsGroup = compositionLocalOf { false }

/** HyperOS settings page: grey page, collapsing large title, back chevron. */
@Composable
internal fun MiuixSettingsScaffold(
    title: String,
    onBackClick: () -> Unit,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val scrollBehavior = MiuixScrollBehavior()
    val haptics = rememberAppHaptics()
    val page = MaterialTheme.colorScheme.background
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MxTopAppBar(
                title = title,
                subtitle = subtitle ?: "",
                color = page,
                navigationIcon = {
                    MxIconButton(
                        onClick = {
                            haptics.perform(HapticKind.LIGHT)
                            onBackClick()
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        M3Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = page,
    ) { padding ->
        if (windowSizeInfo.isTablet) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) { content(padding) }
            }
        } else {
            content(padding)
        }
    }
}

/** A titled HyperOS card holding a group of rows. */
@Composable
internal fun MiuixSettingsGroup(title: String?, items: List<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) SmallTitle(text = title)
        MxCard(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp, top = if (title == null) 12.dp else 0.dp)
        ) {
            CompositionLocalProvider(LocalMiuixSettingsGroup provides true) {
                items.forEach { it(androidx.compose.ui.graphics.RectangleShape) }
            }
        }
    }
}

@Composable
internal fun MiuixSettingsTitle(title: String) {
    SmallTitle(text = title, modifier = Modifier.padding(top = 8.dp))
}

/**
 * One settings row as a HyperOS preference: an arrow row, a switch row or a plain row, with the
 * row's icon in a rounded tile and the app's search highlight preserved.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MiuixSettingsItem(
    title: String,
    subtitle: String?,
    trailingText: String?,
    icon: ImageVector?,
    iconRes: Int?,
    iconTint: Color?,
    iconContainerColor: Color?,
    onClick: (() -> Unit)?,
    hasSwitch: Boolean,
    switchState: Boolean,
    onSwitchChange: ((Boolean) -> Unit)?,
    hasSlider: Boolean,
    sliderValue: Float,
    sliderRange: ClosedFloatingPointRange<Float>,
    onSliderChange: ((Float) -> Unit)?,
    titleColor: Color,
    modifier: Modifier,
    trailingContent: (@Composable () -> Unit)?,
    highlightKey: String?,
) {
    val haptics = rememberAppHaptics()
    val isHighlighted = SettingsHighlightManager.isHighlighted(highlightKey)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val highlightAlpha = remember { Animatable(0f) }
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val isScrolling = SettingsHighlightManager.isScrollingToTarget
    LaunchedEffect(isHighlighted, isScrolling) {
        if (isHighlighted) {
            if (isScrolling) {
                delay(900)
                SettingsHighlightManager.isScrollingToTarget = false
            }
            delay(150)
            try {
                bringIntoViewRequester.bringIntoViewCentered(itemCoordinates)
            } catch (_: Exception) {}
            repeat(2) {
                highlightAlpha.animateTo(1f, tween(200, easing = LinearEasing))
                highlightAlpha.animateTo(0f, tween(200, easing = LinearEasing))
            }
            highlightAlpha.animateTo(1f, tween(200, easing = LinearEasing))
            delay(1200)
            highlightAlpha.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
            SettingsHighlightManager.clearHighlight(highlightKey)
        }
    }
    val mx = MiuixTheme.colorScheme
    val rowModifier = modifier
        .fillMaxWidth()
        .bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { itemCoordinates = it }
        .background(mx.primary.copy(alpha = 0.18f * highlightAlpha.value))

    val start: (@Composable () -> Unit)? = if (icon != null || iconRes != null) {
        {
            Box(
                Modifier
                    .padding(end = 14.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconContainerColor ?: mx.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val tint = iconTint ?: mx.onTertiaryContainer
                if (iconRes != null) {
                    M3Icon(painterResource(iconRes), null, Modifier.size(22.dp), tint = tint)
                } else if (icon != null) {
                    M3Icon(icon, null, Modifier.size(22.dp), tint = tint)
                }
            }
        }
    } else {
        null
    }
    val titleColors = BasicComponentDefaults.titleColor(color = if (titleColor == MaterialTheme.colorScheme.onSurface) mx.onSurface else titleColor)
    val trailing: @Composable RowScope.() -> Unit = {
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = mx.onSurfaceVariantActions,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp).widthIn(max = 140.dp)
            )
        }
        if (trailingContent != null) trailingContent()
    }
    val slider: (@Composable () -> Unit)? = if (hasSlider && onSliderChange != null) {
        { MxSlider(value = sliderValue, onValueChange = onSliderChange, valueRange = sliderRange, modifier = Modifier.padding(top = 12.dp)) }
    } else {
        null
    }

    when {
        hasSwitch && onSwitchChange != null && onClick == null -> SwitchPreference(
            checked = switchState,
            onCheckedChange = {
                haptics.perform(HapticKind.UI)
                onSwitchChange(it)
            },
            title = title,
            summary = subtitle,
            titleColor = titleColors,
            startAction = start,
            endActions = trailing,
            bottomAction = slider,
            modifier = rowModifier,
        )

        onClick != null -> ArrowPreference(
            title = title,
            summary = subtitle,
            titleColor = titleColors,
            startAction = start,
            endActions = {
                trailing()
                if (hasSwitch && onSwitchChange != null) {
                    top.yukonga.miuix.kmp.basic.Switch(checked = switchState, onCheckedChange = onSwitchChange)
                }
            },
            bottomAction = slider,
            onClick = {
                haptics.perform(HapticKind.MEDIUM)
                onClick()
            },
            modifier = rowModifier,
        )

        else -> BasicComponent(
            title = title,
            summary = subtitle,
            titleColor = titleColors,
            startAction = start,
            endActions = trailing,
            bottomAction = slider,
            modifier = rowModifier,
        )
    }
    Spacer(Modifier.size(0.dp))
}
