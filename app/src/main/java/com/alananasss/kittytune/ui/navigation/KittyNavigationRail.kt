package com.alananasss.kittytune.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
    modifier: Modifier = Modifier,
    style: String = "classic",
    showLabels: Boolean = true,
    windowSizeInfo: com.alananasss.kittytune.ui.common.WindowSizeInfo? = null
) {
    val visibleTabs = tabs.filter { it.visible }.ifEmpty { tabs }
    val track = playerViewModel.uiCurrentTrack
    val isPlaying = playerViewModel.uiIsPlaying
    val info = windowSizeInfo ?: rememberWindowSizeInfo()
    val haptics = rememberAppHaptics()
    val isTablet = info.isTablet
    val isPhoneLandscape = info.heightSizeClass == WindowHeightSizeClass.COMPACT

    val railWidth = if (isTablet) {
        if (showLabels && style == "classic") 84.dp else 74.dp
    } else {
        if (style == "modern") if (showLabels) 150.dp else 84.dp
        else if (info.widthSizeClass == WindowWidthSizeClass.EXPANDED) 260.dp else 220.dp
    }

    // TABLET: MODERN MODE
    if (isTablet && style == "modern") {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(railWidth)
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .padding(horizontal = 6.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    visibleTabs.forEach { tab ->
                        val isSelected = selectedRoute == tab.route
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            label = "modernTabBg"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "modernTabContent"
                        )

                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(containerColor)
                                .clickable {
                                    haptics.perform(HapticKind.UI)
                                    onTabSelected(tab)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .clickable {
                                haptics.perform(HapticKind.MEDIUM)
                                onFabClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = fabIcon,
                            contentDescription = fabLabel,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // TABLET (modern): the mini player at the bottom of the rail is gone - same
            // decision as in the classic branch below (the duplicate in the bottom-left
            // corner was removed; the phone layout is unaffected).
        }
        return
    }

    // TABLET: CLASSIC MODE
    if (isTablet) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(railWidth)
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                visibleTabs.forEach { tab ->
                    val selected = selectedRoute == tab.route
                    val bgColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        label = "classicTabBg"
                    )
                    val contentColor = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptics.perform(HapticKind.UI)
                                onTabSelected(tab)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (showLabels) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            haptics.perform(HapticKind.MEDIUM)
                            onFabClick()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = fabIcon,
                            contentDescription = fabLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (showLabels) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = fabLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // TABLET: the mini player at the bottom of the rail is no longer drawn - playback
            // on a tablet with the side panel shows the floating mini player at the bottom
            // of the content area, and the duplicate strip in the bottom-left corner only
            // got in the way. The phone branch below is untouched: there the in-rail
            // player stays as it was.
        }
        return
    }

    // PHONE: UNCHANGED (wide bar pinned to the top)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
            .padding(start = 4.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Top
        ) {
            visibleTabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                val bgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "phoneTabBg"
                )
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isPhoneLandscape) 2.dp else 4.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                    if (showLabels) {
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = tab.title,
                            style = if (isPhoneLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(if (isPhoneLandscape) 8.dp else 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isPhoneLandscape) 22.dp else 26.dp)
                )
                if (showLabels) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = fabLabel,
                        style = if (isPhoneLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (track != null && !info.isTablet) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
