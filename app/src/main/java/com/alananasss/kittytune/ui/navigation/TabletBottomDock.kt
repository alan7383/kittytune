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
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.glass.GlassComponent
import com.alananasss.kittytune.ui.glass.components.GlassChromeButton
import com.alananasss.kittytune.ui.glass.components.GlassChromePanel
import com.alananasss.kittytune.ui.glass.components.GlassNavItem
import com.alananasss.kittytune.ui.glass.components.GlassNavigationTabs
import com.alananasss.kittytune.ui.glass.glassEnabled
import com.alananasss.kittytune.ui.design.IconButton
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.design.Icon

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
    val track = playerViewModel.uiCurrentTrack
    val isPlaying = playerViewModel.uiIsPlaying
    val showMiniPlayer = track != null && !playerViewModel.isSidePlayerOpen
    val glassNavigation = glassEnabled(GlassComponent.NAVIGATION)
    val glassMiniPlayer = glassEnabled(GlassComponent.MINI_PLAYER)
    val miuixNavigation = com.alananasss.kittytune.ui.design.miuixEnabled(com.alananasss.kittytune.ui.design.MiuixComponent.NAVIGATION)

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
        // Extended Navigation Dock (Spotify tablet style)
        if (glassNavigation) {
            val visibleTabs = tabs.filter { it.visible }
            val items = remember(visibleTabs) { visibleTabs.map { GlassNavItem(it.route, it.title, it.icon) } }
            Row(
                modifier = if (showMiniPlayer) {
                    Modifier.weight(1.25f)
                } else {
                    Modifier
                        .widthIn(min = 480.dp, max = 640.dp)
                        .fillMaxWidth()
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassNavigationTabs(
                    items = items,
                    selectedKey = selectedRoute,
                    onSelect = { item ->
                        haptics.perform(HapticKind.UI)
                        visibleTabs.firstOrNull { it.route == item.key }?.let(onTabSelected)
                    },
                    height = 68.dp,
                    modifier = Modifier.weight(1f)
                )
                GlassChromeButton(
                    icon = fabIcon,
                    contentDescription = fabLabel,
                    size = 68.dp,
                    onClick = {
                        haptics.perform(HapticKind.MEDIUM)
                        onFabClick()
                    }
                )
            }
        } else if (miuixNavigation) {
            val visibleTabs = tabs.filter { it.visible }
            Row(
                modifier = if (showMiniPlayer) Modifier.weight(1.25f) else Modifier.widthIn(min = 480.dp, max = 640.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                top.yukonga.miuix.kmp.basic.FloatingNavigationBar(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    horizontalOutSidePadding = 0.dp,
                    defaultWindowInsetsPadding = false,
                ) {
                    visibleTabs.forEach { tab ->
                        top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem(
                            selected = selectedRoute == tab.route,
                            onClick = {
                                haptics.perform(HapticKind.UI)
                                onTabSelected(tab)
                            },
                            icon = com.alananasss.kittytune.ui.design.miuixIconOrSelf(tab.icon),
                            label = tab.title,
                        )
                    }
                    top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem(
                        selected = false,
                        onClick = {
                            haptics.perform(HapticKind.MEDIUM)
                            onFabClick()
                        },
                        icon = com.alananasss.kittytune.ui.design.miuixIconOrSelf(fabIcon),
                        label = fabLabel,
                    )
                }

            }
        } else {
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
                .height(72.dp)
        ) {
            if (track != null) {
                val dockPlayerContent: @Composable () -> Unit = {
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
                if (glassMiniPlayer) {
                    GlassChromePanel(
                        shape = com.kyant.shapes.RoundedRectangle(36.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentModifier = Modifier.clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            haptics.perform(HapticKind.LIGHT)
                            onPlayerClick()
                        }
                    ) {
                        dockPlayerContent()
                    }
                } else {
                    Surface(
                        onClick = {
                            haptics.perform(HapticKind.LIGHT)
                            onPlayerClick()
                        },
                        shape = RoundedCornerShape(36.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        dockPlayerContent()
                    }
                }
            }
        }
    }
}
