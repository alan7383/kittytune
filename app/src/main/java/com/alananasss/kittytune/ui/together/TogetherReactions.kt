package com.alananasss.kittytune.ui.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherReaction
import kotlin.random.Random

private const val REACTION_COLUMNS = 8
private const val REACTION_ROWS = 6
private const val PARTICLES_PER_REACTION = 3
private const val MAX_FLOATING_REACTIONS = REACTION_COLUMNS * REACTION_ROWS

@Composable
internal fun TogetherReactionButton(
    roomCode: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val activeCode by Together.active.collectAsState()
    val code = activeCode ?: return
    if (roomCode != null && roomCode != code) return
    val connected by Together.isConnected.collectAsState()
    var expanded by remember(code) { mutableStateOf(false) }
    LaunchedEffect(connected) {
        if (!connected) expanded = false
    }
    val label = stringResource(R.string.together_reactions)

    Box {
        IconButton(onClick = { expanded = true }, enabled = connected) {
            Icon(
                Icons.Rounded.AddReaction,
                contentDescription = label,
                tint = if (connected) tint else tint.copy(alpha = 0.38f),
            )
        }
        DropdownMenu(expanded = expanded && connected, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                TogetherReaction.entries.chunked(4).forEach { reactions ->
                    Row {
                        reactions.forEach { reaction ->
                            IconButton(onClick = hapticAction(HapticKind.UI) { Together.sendReaction(code, reaction) }) {
                                Text(reaction.emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TogetherReactionOverlay(modifier: Modifier = Modifier) {
    val activeCode by Together.active.collectAsState()
    val code = activeCode ?: return
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return
    val floating = remember(code) { mutableStateListOf<FloatingReaction>() }

    LaunchedEffect(code) {
        var nextId = 0L
        Together.reactions.collect { event ->
            if (event.code == code) {
                val overflow = (floating.size + PARTICLES_PER_REACTION - MAX_FLOATING_REACTIONS).coerceAtLeast(0)
                repeat(overflow) { floating.removeAt(0) }
                val occupiedCells = floating.mapTo(mutableSetOf()) { it.cell }
                val availableCells = (0 until MAX_FLOATING_REACTIONS).filterNot { it in occupiedCells }.toMutableList()
                repeat(PARTICLES_PER_REACTION) { index ->
                    val cell = availableCells.removeAt(Random.nextInt(availableCells.size))
                    floating.add(FloatingReaction(nextId++, event.reaction.emoji, cell, delayMillis = index * 35))
                }
            }
        }
    }

    if (floating.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics {}) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cellWidth = with(density) { (maxWidth - 32.dp).coerceAtLeast(0.dp).toPx() / REACTION_COLUMNS }
        val cellHeight = with(density) { (maxHeight - 44.dp).coerceAtLeast(0.dp).toPx() / REACTION_ROWS }
        floating.forEach { reaction ->
            key(reaction.id) {
                val progress = remember { Animatable(0f) }
                val sway = remember { Random(reaction.id).nextFloat() - 0.5f }
                val column = reaction.cell % REACTION_COLUMNS
                val row = reaction.cell / REACTION_COLUMNS
                val x = cellWidth * (column + 0.5f - REACTION_COLUMNS / 2f)
                val y = cellHeight * (row + 0.5f - REACTION_ROWS / 2f)
                LaunchedEffect(Unit) {
                    progress.animateTo(
                        1f,
                        tween(durationMillis = 2_700, delayMillis = reaction.delayMillis, easing = LinearEasing),
                    )
                    floating.remove(reaction)
                }
                Text(
                    text = reaction.emoji,
                    fontSize = 34.sp,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 3f), 9f),
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            val fraction = progress.value
                            translationX = x + cellWidth * sway * 0.45f * fraction
                            translationY = y + cellHeight * (0.2f - 0.5f * fraction)
                            alpha = minOf(fraction * 12f, (1f - fraction) * 3f, 1f)
                            scaleX = 0.8f + minOf(fraction * 3f, 0.3f)
                            scaleY = scaleX
                        },
                )
            }
        }
    }
}

private data class FloatingReaction(val id: Long, val emoji: String, val cell: Int, val delayMillis: Int)
