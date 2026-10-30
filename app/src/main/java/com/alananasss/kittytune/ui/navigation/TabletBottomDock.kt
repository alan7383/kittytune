package com.alananasss.kittytune.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.liquidglass.LocalBackdrop
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidBottomTab
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidBottomTabs
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidButton
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.alananasss.kittytune.ui.design.IconButton
import com.alananasss.kittytune.ui.design.Surface

@Composable
fun TabletBottomDock(
    tabs: List<KittyTab>,
    selectedRoute: String?,
    onTabSelected: (KittyTab) -> Unit,
    onFabClick: () -> Unit,
    fabIcon: androidx.compose.ui.graphics.vector.ImageVector,
    fabLabel: String,
    playerViewModel: PlayerViewModel,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerPrefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences(context) }
    val haptics = rememberAppHaptics()
    val uiDesignStyle = LocalUiDesignStyle.current
    val liquidConfig = LocalLiquidGlassConfig.current
    val backdrop = LocalBackdrop.current
    val isLiquidGlass = uiDesignStyle == UiDesignStyle.LIQUID_GLASS && backdrop != null && liquidConfig.applyToBottomBar
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer

    val track = playerViewModel.uiCurrentTrack
    val isPlaying = playerViewModel.uiIsPlaying
    val showMiniPlayer = track != null && !playerViewModel.isSidePlayerOpen

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .semantics { }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = if (showMiniPlayer) Arrangement.spacedBy(16.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val visibleTabs = remember(tabs) { tabs.filter { it.visible } }
        val selectedTabIndex = remember(visibleTabs, selectedRoute) {
            val idx = visibleTabs.indexOfFirst { it.route == selectedRoute }
            if (idx >= 0) idx else 0
        }

        if (isLiquidGlass) {
            Row(
                modifier = if (showMiniPlayer) {
                    Modifier.weight(1.25f)
                } else {
                    Modifier
                        .widthIn(min = 480.dp, max = 620.dp)
                        .fillMaxWidth()
                },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KittyLiquidBottomTabs(
                    selectedTabIndex = { selectedTabIndex },
                    onTabSelected = { index ->
                        visibleTabs.getOrNull(index)?.let { tab ->
                            haptics.perform(HapticKind.UI)
                            onTabSelected(tab)
                        }
                    },
                    backdrop = backdrop,
                    tabsCount = visibleTabs.size,
                    showIndicator = selectedRoute != null,
                    modifier = Modifier.weight(1f, fill = false),
                    accentColor = MaterialTheme.colorScheme.primary
                ) {
                    visibleTabs.forEachIndexed { index, tab ->
                        KittyLiquidBottomTab(
                            onClick = {
                                haptics.perform(HapticKind.UI)
                                onTabSelected(tab)
                            }
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                KittyLiquidButton(
                    onClick = {
                        haptics.perform(HapticKind.MEDIUM)
                        onFabClick()
                    },
                    backdrop = backdrop,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(0.dp),
                    surfaceColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    tint = MaterialTheme.colorScheme.tertiary
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = fabIcon,
                            contentDescription = fabLabel,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = fabLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            // Extended Navigation Dock (Spotify tablet style)
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                ),
                modifier = if (showMiniPlayer) {
                    Modifier
                        .weight(1.25f)
                        .height(72.dp)
                } else {
                    Modifier
                        .widthIn(min = 480.dp, max = 580.dp)
                        .fillMaxWidth()
                        .height(72.dp)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.filter { it.visible }.forEach { tab ->
                        val isSelected = selectedRoute == tab.route
                        val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable {
                                    haptics.perform(HapticKind.UI)
                                    onTabSelected(tab)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Action FAB item (Profile / Settings / Recognition...)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                haptics.perform(HapticKind.MEDIUM)
                                onFabClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = fabIcon,
                                contentDescription = fabLabel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = fabLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Extended standalone MiniPlayer on the right
        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeOut(),
            modifier = Modifier
                .weight(1f)
                .height(if (isLiquidGlass) 64.dp else 72.dp)
        ) {
            if (track != null) {
                Surface(
                    onClick = {
                        haptics.perform(HapticKind.LIGHT)
                        onPlayerClick()
                    },
                    shape = if (isLiquidGlass) RoundedRectangle((28f * liquidConfig.cornerRadiusFactor).dp) else RoundedCornerShape(36.dp),
                    color = if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = if (isLiquidGlass) 0.dp else 6.dp,
                    shadowElevation = if (isLiquidGlass) 0.dp else 8.dp,
                    border = if (isLiquidGlass) null else BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isLiquidGlass) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedRectangle((28f * liquidConfig.cornerRadiusFactor).dp) },
                                    effects = {
                                        if (liquidConfig.vibrancyEnabled) {
                                            vibrancy()
                                        }
                                        blur(liquidConfig.blurRadiusDp.dp.toPx())
                                        lens(
                                            refractionHeight = liquidConfig.refractionHeightDp.dp.toPx(),
                                            refractionAmount = liquidConfig.refractionAmountDp.dp.toPx(),
                                            depthEffect = liquidConfig.depthEffect,
                                            chromaticAberration = liquidConfig.chromaticAberration > 0f
                                        )
                                    },
                                    highlight = {
                                        Highlight.Default.copy(alpha = (0.7f * liquidConfig.highlightIntensity).coerceIn(0f, 1f))
                                    },
                                    shadow = if (liquidConfig.shadowsEnabled) {
                                        {
                                            Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.15f))
                                        }
                                    } else null,
                                    onDrawSurface = {
                                        drawRect(surfaceContainerColor.copy(alpha = liquidConfig.surfaceAlpha))
                                    }
                                )
                            } else Modifier
                        )
                        .clipToBounds()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 10.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )

                            Spacer(Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = track.title ?: stringResource(R.string.untitled_track),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val deviceLabel = playerViewModel.playbackDeviceLabel
                                    if (deviceLabel != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                haptics.perform(HapticKind.LIGHT)
                                                playerViewModel.showConnectSheet = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Devices,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = deviceLabel,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Bouton Like
                            IconButton(
                                onClick = { playerViewModel.toggleLike() },
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (playerViewModel.uiIsLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (playerViewModel.uiIsLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            if (playerViewModel.isYourMixActive) {
                                IconButton(
                                    onClick = { playerViewModel.dislikeCurrentTrackInMix() },
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ThumbDown,
                                        contentDescription = stringResource(R.string.mix_dislike),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Bouton Lecture / Pause
                            IconButton(
                                onClick = { playerViewModel.togglePlayPause() },
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(if (isPlaying) R.string.btn_pause else R.string.btn_play),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Bouton Suivant
                            IconButton(
                                onClick = { playerViewModel.requestSkipNext() },
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        val progress = rememberDockProgress(playerViewModel)

                        LinearProgressIndicator(
                            progress = { progress.value },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }
}
