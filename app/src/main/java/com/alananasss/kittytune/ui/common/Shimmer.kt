package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// the magic gradient that moves across the screen
@Composable
private fun ShimmerBrush(targetValue: Float = 1000f): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000)
        ), label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

// easy extension to slap the shimmer on any composable
fun Modifier.shimmerBackground(shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
    this
        .background(ShimmerBrush(), shape)
        .clip(shape)
}

// --- basic building blocks ---

// just looks like a line of text
@Composable
fun ShimmerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(16.dp)
            .shimmerBackground(RoundedCornerShape(8.dp))
    )
}

// generic box placeholder
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.shimmerBackground(RoundedCornerShape(12.dp))
    )
}


// --- specific skeletons ---

// pretend this is a song row in a list
@Composable
fun TrackListItemShimmer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.7f))
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.4f))
        }
    }
}

// square card placeholder (playlists/albums)
@Composable
fun SquareCardShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(140.dp)) {
        ShimmerBox(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        ShimmerLine(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
        Spacer(Modifier.height(4.dp))
        ShimmerLine(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
    }
}

// round placeholder for artist profiles
@Composable
fun ArtistCircleShimmer(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(120.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        ShimmerLine(modifier = Modifier.fillMaxWidth(0.7f))
    }
}