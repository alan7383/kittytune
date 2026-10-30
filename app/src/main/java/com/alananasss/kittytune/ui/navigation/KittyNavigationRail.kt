package com.alananasss.kittytune.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.common.WindowWidthSizeClass
import com.alananasss.kittytune.ui.common.WindowHeightSizeClass
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.common.HapticKind

import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalBackdrop
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.alananasss.kittytune.ui.design.IconButton

@Composable
fun KittyNavigationRail(
    tabs: List<KittyTab>,
    selectedRoute: String?,
    onTabSelected: (KittyTab) -> Unit,
    onFabClick: () -> Unit,
    fabIcon: ImageVector,
    fabLabel: String,
    playerViewModel: PlayerViewModel,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleTabs = tabs.filter { it.visible }
    val track = playerViewModel.uiCurrentTrack
    val isPlaying = playerViewModel.uiIsPlaying
    val windowSizeInfo = rememberWindowSizeInfo()
    val haptics = rememberAppHaptics()
    val uiDesignStyle = LocalUiDesignStyle.current
    val liquidConfig = LocalLiquidGlassConfig.current
    val backdrop = LocalBackdrop.current
    val isLiquidGlass = uiDesignStyle == UiDesignStyle.LIQUID_GLASS && backdrop != null && liquidConfig.applyToBottomBar
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer

    val isPhoneLandscape = windowSizeInfo.heightSizeClass == WindowHeightSizeClass.COMPACT

    // Adaptive width: wider on tablet, standard size on landscape phone
    val railWidth = if (windowSizeInfo.widthSizeClass == WindowWidthSizeClass.EXPANDED) 260.dp else 220.dp
    val railCornerRadius = (24f * liquidConfig.cornerRadiusFactor).dp

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .then(
                if (isLiquidGlass) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(topEnd = railCornerRadius, bottomEnd = railCornerRadius) },
                        effects = {
                            if (liquidConfig.vibrancyEnabled) {
                                vibrancy()
                            }
                            blur(liquidConfig.blurRadiusDp.dp.toPx())
                            lens(
                                refractionHeight = liquidConfig.refractionHeightDp.dp.toPx() * 0.5f,
                                refractionAmount = liquidConfig.refractionAmountDp.dp.toPx() * 0.5f,
                                depthEffect = liquidConfig.depthEffect,
                                chromaticAberration = liquidConfig.chromaticAberration > 0f
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = (0.5f * liquidConfig.highlightIntensity).coerceIn(0f, 1f))
                        },
                        shadow = if (liquidConfig.shadowsEnabled) {
                            {
                                Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.15f))
                            }
                        } else null,
                        onDrawSurface = {
                            drawRect(surfaceContainerColor.copy(alpha = liquidConfig.surfaceAlpha))
                        }
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                }
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom))
    ) {
        // Status bar spacing
        Spacer(Modifier.height(16.dp))

        // Navigation items (Icon + Text horizontally)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            visibleTabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                val bgColor by animateColorAsState(
                    targetValue = if (selected) {
                        if (isLiquidGlass) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.secondaryContainer
                    } else Color.Transparent,
                    label = "railTabBg"
                )
                val contentColor = if (selected) {
                    if (isLiquidGlass) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSecondaryContainer
                } else MaterialTheme.colorScheme.onSurfaceVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isPhoneLandscape) 2.dp else 4.dp)
                        .clip(if (isLiquidGlass) RoundedCornerShape(16.dp) else RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .clickable {
                            haptics.perform(HapticKind.UI)
                            onTabSelected(tab)
                        }
                        .padding(horizontal = 16.dp, vertical = if (isPhoneLandscape) 10.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = contentColor,
                        modifier = Modifier.size(if (isPhoneLandscape) 22.dp else 26.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = tab.title,
                        style = if (isPhoneLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isLiquidGlass) 0.25f else 0.5f), 
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(if (isPhoneLandscape) 8.dp else 16.dp))

            // Action FAB item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isLiquidGlass) RoundedCornerShape(16.dp) else RoundedCornerShape(8.dp))
                    .then(
                        if (isLiquidGlass) {
                            Modifier.background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                        } else Modifier
                    )
                    .clickable {
                        haptics.perform(HapticKind.MEDIUM)
                        onFabClick()
                    }
                    .padding(horizontal = 16.dp, vertical = if (isPhoneLandscape) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = fabIcon,
                    contentDescription = "Action",
                    tint = if (isLiquidGlass) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isPhoneLandscape) 22.dp else 26.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = fabLabel,
                    style = if (isPhoneLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isLiquidGlass) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Horizontal MiniPlayer at bottom of rail
        if (track != null) {
            val miniCornerRadius = (20f * liquidConfig.cornerRadiusFactor).dp
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(if (isLiquidGlass) RoundedCornerShape(miniCornerRadius) else RoundedCornerShape(12.dp))
                    .then(
                        if (isLiquidGlass) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(miniCornerRadius) },
                                effects = {
                                    vibrancy()
                                    blur(liquidConfig.blurRadiusDp.dp.toPx())
                                    lens(
                                        refractionHeight = liquidConfig.refractionHeightDp.dp.toPx() * 0.5f,
                                        refractionAmount = liquidConfig.refractionAmountDp.dp.toPx() * 0.75f,
                                        depthEffect = liquidConfig.depthEffect,
                                        chromaticAberration = liquidConfig.chromaticAberration > 0f
                                    )
                                },
                                highlight = {
                                    Highlight.Default.copy(alpha = 0.6f)
                                },
                                shadow = {
                                    Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.15f))
                                },
                                onDrawSurface = {
                                    drawRect(surfaceContainerColor.copy(alpha = liquidConfig.surfaceAlpha))
                                }
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    )
                    .clickable {
                        haptics.perform(HapticKind.LIGHT)
                        onPlayerClick()
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (isPhoneLandscape) 40.dp else 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title ?: "",
                        style = if (isPhoneLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.displayArtist,
                        style = if (isPhoneLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.size(if (isPhoneLandscape) 32.dp else 36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isPhoneLandscape) 20.dp else 24.dp)
                    )
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}
