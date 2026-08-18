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
    val track = playerViewModel.currentTrack
    val isPlaying = playerViewModel.isPlaying
    val windowSizeInfo = rememberWindowSizeInfo()

    val isPhoneLandscape = windowSizeInfo.heightSizeClass == WindowHeightSizeClass.COMPACT

    // Adaptive width: wider on tablet, standard size on landscape phone
    val railWidth = if (windowSizeInfo.widthSizeClass == WindowWidthSizeClass.EXPANDED) 260.dp else 220.dp

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(MaterialTheme.colorScheme.surface)
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
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                )
                val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isPhoneLandscape) 2.dp else 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .clickable { onTabSelected(tab) }
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
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), 
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(if (isPhoneLandscape) 8.dp else 16.dp))

            // Action FAB item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFabClick() }
                    .padding(horizontal = 16.dp, vertical = if (isPhoneLandscape) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = fabIcon,
                    contentDescription = "Action",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isPhoneLandscape) 22.dp else 26.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = fabLabel,
                    style = if (isPhoneLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Horizontal MiniPlayer at bottom of rail
        if (track != null) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onPlayerClick() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.fullResArtwork,
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
