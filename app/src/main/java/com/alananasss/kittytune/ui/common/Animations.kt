package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(24f) }
    
    LaunchedEffect(Unit) {
        // Apply stagger delay only to the initial items that fit on screen.
        // Items loaded later via fast scrolling will fade in immediately without artificial waiting.
        val staggerDelay = if (index < 12) (index * 40L) else 0L
        delay(staggerDelay)
        launch { alpha.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing)) }
        launch { offsetY.animateTo(0f, animationSpec = tween(250, easing = FastOutSlowInEasing)) }
    }
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value * density
            }
    ) {
        content()
    }
}
