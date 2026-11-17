@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.alananasss.kittytune.ui.together

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.together.Together
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ─── Material 3 Expressive Scalloped Cookie Shapes ──────────────────────────────────────────

internal enum class ExpressiveCookieProfile { SINE, LOBES, CIRCLE }

@Immutable
internal data class ExpressiveCookieSpec(
    val profile: ExpressiveCookieProfile = ExpressiveCookieProfile.SINE,
    val waves: Int = 12,
    val amplitude: Float = 0.065f,
)

internal fun buildExpressiveCookiePath(
    spec: ExpressiveCookieSpec,
    cx: Float,
    cy: Float,
    radius: Float,
    path: Path = Path(),
): Path {
    path.reset()
    val waves = spec.waves.coerceIn(3, 24)
    val amp = if (spec.profile == ExpressiveCookieProfile.CIRCLE) 0f else spec.amplitude.coerceIn(0f, 0.16f)
    val steps = (waves * 20).coerceIn(120, 360)
    for (i in 0 until steps) {
        val t = i.toDouble() / steps * 2.0 * PI
        val k = when (spec.profile) {
            ExpressiveCookieProfile.SINE -> 1f - amp + amp * cos(waves * t).toFloat()
            ExpressiveCookieProfile.LOBES -> 1f - amp + amp * abs(cos(waves / 2.0 * t)).toFloat()
            ExpressiveCookieProfile.CIRCLE -> 1f
        }
        val a = t - PI / 2.0
        val x = cx + radius * k * cos(a).toFloat()
        val y = cy + radius * k * sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

internal fun Modifier.expressiveCookieBackground(
    color: Color,
    spec: ExpressiveCookieSpec = ExpressiveCookieSpec(),
    rotationDegrees: () -> Float = { 0f },
): Modifier = this.drawWithCache {
    val path = buildExpressiveCookiePath(
        spec = spec,
        cx = size.width / 2f,
        cy = size.height / 2f,
        radius = min(size.width, size.height) / 2f,
    )
    onDrawBehind {
        rotate(rotationDegrees(), center) {
            drawPath(path = path, color = color)
        }
    }
}

/**
 * Material 3 Expressive rotating scalloped cookie badge (inspired by Expressive-Keyboard HeroCard).
 */
@Composable
internal fun ExpressiveCookieBadge(
    isActive: Boolean,
    size: Dp = 64.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    spec: ExpressiveCookieSpec = ExpressiveCookieSpec(ExpressiveCookieProfile.SINE, 12, 0.065f),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        animationSpec = tween(280),
        label = "cookieBg",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "cookieSpin")
    val spinningAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cookieAngle",
    )
    val settledAngle by animateFloatAsState(
        targetValue = if (isActive) 24f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 180f),
        label = "cookieSettle",
    )

    Box(
        modifier = modifier
            .size(size)
            .expressiveCookieBackground(
                color = bgColor,
                spec = spec,
                rotationDegrees = { if (isActive) spinningAngle else settledAngle },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ─── Android 16 / Essentials Grouped Cards with Spring Corner Morphing ──────────────────────

@Composable
internal fun TogetherGroupSection(
    title: String? = null,
    trailingHeader: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    spacing: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null || trailingHeader != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                trailingHeader?.invoke()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius)),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@Composable
internal fun TogetherGroupItem(
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    containerColor: Color = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    },
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) 22.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "groupItemCorner",
    )
    val animatedBg by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(220),
        label = "groupItemBg",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(animatedBg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}

@Composable
internal fun TogetherIconBadge(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─── Connected M3 Expressive Segmented Picker (Essentials + Expressive-Keyboard) ─────────────

internal data class TogetherTabOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val badgeCount: Int = 0,
)

@Composable
internal fun <T> TogetherSegmentedTabs(
    options: List<TogetherTabOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            val weight by animateFloatAsState(
                targetValue = if (isSelected) 1.2f else 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                label = "tabWeight_$index",
            )
            Box(modifier = Modifier.weight(weight)) {
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onSelect(option.value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Tab },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (option.icon != null) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        if (option.badgeCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ) {
                                Text(
                                    text = option.badgeCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Solid Cover & Member Chips (No Gradients!) ─────────────────────────────────────────────

@Composable
internal fun TogetherArtwork(
    url: String?,
    isLive: Boolean,
    size: Dp,
    cornerRadius: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(cs.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isLive) {
                Surface(
                    shape = CircleShape,
                    color = cs.primaryContainer.copy(alpha = 0.94f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                        ListeningBars(
                            isPlaying = true,
                            modifier = Modifier.size(14.dp),
                            color = cs.onPrimaryContainer,
                        )
                    }
                }
            }
        } else if (isLive) {
            ListeningBars(
                isPlaying = true,
                modifier = Modifier.size(size * 0.42f),
                color = cs.onSecondaryContainer,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = cs.onSecondaryContainer,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}

@Composable
internal fun TogetherMemberPill(
    name: String,
    isHost: Boolean,
    isMe: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (isHost) cs.primaryContainer else cs.surfaceContainerHighest
    val content = if (isHost) cs.onPrimaryContainer else cs.onSurface
    val badgeBg = if (isHost) cs.primary else cs.secondaryContainer
    val badgeFg = if (isHost) cs.onPrimary else cs.onSecondaryContainer

    Surface(
        shape = CircleShape,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.trim().take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeFg,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = name.ifBlank { stringResource(R.string.together_title) },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isMe || isHost) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isHost) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.together_host_badge),
                    tint = cs.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// ─── Live Synced Wavy Progress Bar (Pure Sine Wave, No Gradients) ───────────────────────────

@Composable
internal fun TogetherLiveProgressBar(
    room: Together.Room,
    modifier: Modifier = Modifier,
) {
    val durationMs = room.nowPlaying?.durationMs?.takeIf { it > 0L } ?: return
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.isPlaying, room.stateReceivedAt, room.nowPlaying?.id) {
        nowMs = System.currentTimeMillis()
        while (room.isPlaying) {
            delay(250L)
            nowMs = System.currentTimeMillis()
        }
    }

    val currentMs = room.currentPositionMs(nowMs)
    val fraction = (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "liveWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liveWavePhase",
    )
    val ampFraction by animateFloatAsState(
        targetValue = if (room.isPlaying) 1f else 0f,
        animationSpec = tween(260),
        label = "liveWaveAmp",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
        ) {
            val w = size.width
            val cy = size.height / 2f
            val strokePx = with(density) { 3.5.dp.toPx() }
            val ampPx = with(density) { 3.dp.toPx() } * ampFraction
            val waveLenPx = with(density) { 26.dp.toPx() }
            val splitX = (w * fraction).coerceIn(0f, w)

            if (splitX < w) {
                drawLine(
                    color = inactiveColor,
                    start = Offset((splitX + strokePx * 1.4f).coerceAtMost(w), cy),
                    end = Offset(w, cy),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            }

            if (splitX > 1f) {
                if (ampPx > 0.2f) {
                    val steps = (splitX / 2.5f).toInt().coerceIn(8, 260)
                    val path = Path().apply {
                        for (i in 0..steps) {
                            val x = splitX * (i.toFloat() / steps)
                            val theta = (x / waveLenPx) * (2f * PI.toFloat()) + wavePhase
                            val y = cy + ampPx * sin(theta)
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                } else {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, cy),
                        end = Offset(splitX, cy),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTrackClock(currentMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTrackClock(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatTrackClock(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return String.format("%d:%02d", sec / 60L, sec % 60L)
}
