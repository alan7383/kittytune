package com.alananasss.kittytune.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UltimateCompletionOverlay(onDismiss: () -> Unit) {
    var phase by remember { mutableStateOf(0) } // 0: animation, 1: thank you message

    // timer to switch to phase 2 after animation
    LaunchedEffect(Unit) {
        delay(8000) // let it run for 8 seconds
        phase = 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // --- PHASE 1: ANIMATION ---
        AnimatedVisibility(visible = phase == 0, enter = fadeIn(), exit = fadeOut()) {
            Box(contentAlignment = Alignment.Center) {
                // light rays
                val infiniteTransition = rememberInfiniteTransition(label = "rays")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
                    label = "rotation"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = this.center
                    val gradient = Brush.sweepGradient(
                        colors = listOf(Color(0xFFD4AF37), Color(0xFFFFD700), Color(0xFFD4AF37), Color.Transparent),
                        center = center
                    )

                    for (i in 0 until 12) {
                        val angle = rotation + (i * 30f)
                        val endX = center.x + (size.width * 1.5f * cos(Math.toRadians(angle.toDouble()))).toFloat()
                        val endY = center.y + (size.height * 1.5f * sin(Math.toRadians(angle.toDouble()))).toFloat()

                        drawLine(
                            brush = gradient,
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 100f,
                            alpha = 0.08f
                        )
                    }
                }

                // golden record
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse),
                            label = "scale"
                        )

                        Canvas(modifier = Modifier.size(200.dp).scale(scale)) {
                            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE599), Color(0xFFB8860B))))
                            drawCircle(Color.Black, radius = size.width * 0.1f)
                            drawCircle(Color.Black.copy(0.3f), style = Stroke(2f), radius = size.width * 0.3f)
                            drawCircle(Color.Black.copy(0.3f), style = Stroke(2f), radius = size.width * 0.4f)
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        stringResource(R.string.completion_legend),
                        color = Color(0xFFFFD700),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Text(
                        stringResource(R.string.completion_message),
                        color = Color.White.copy(0.8f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // --- PHASE 2: THANKS ---
        AnimatedVisibility(visible = phase == 1, enter = fadeIn(animationSpec = tween(1000))) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    stringResource(R.string.completion_thanks),
                    color = Color(0xFFFFD700),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.completion_thanks_message),
                    color = Color.White.copy(0.8f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(48.dp))
                Text(
                    stringResource(R.string.completion_continue),
                    color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}