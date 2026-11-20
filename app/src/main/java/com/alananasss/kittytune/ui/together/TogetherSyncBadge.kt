package com.alananasss.kittytune.ui.together

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherSyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun TogetherSyncBadge(modifier: Modifier = Modifier, roomCode: String? = null) {
    val activeCode by Together.active.collectAsState()
    val code = activeCode ?: return
    if (roomCode != null && roomCode != code) return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    if (!lifecycle.isAtLeast(Lifecycle.State.STARTED)) return
    val rooms = Together.rooms.collectAsState()
    val isHost by remember(code) { derivedStateOf { rooms.value[code]?.hostId == Together.memberId } }
    if (isHost) {
        val isLocal by remember(code) { derivedStateOf { rooms.value[code]?.nowPlaying?.isLocalOnly == true } }
        if (isLocal) SyncPill(TogetherSyncStatus.LOCAL_TRACK, modifier)
        return
    }

    key(code) {
        val status by produceState<TogetherSyncStatus?>(initialValue = null) {
            while (isActive) {
                value = Together.listenerSyncStatus(code)
                delay(400L)
            }
        }
        status?.let { SyncPill(it, modifier) }
    }
}

@Composable
private fun SyncPill(status: TogetherSyncStatus, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val dotColor = when (status) {
        TogetherSyncStatus.SYNCED -> colors.primary
        TogetherSyncStatus.SYNCING -> colors.tertiary
        TogetherSyncStatus.LOCAL_TRACK -> colors.error
        else -> colors.onSurfaceVariant
    }
    val label = stringResource(
        when (status) {
            TogetherSyncStatus.SYNCED -> R.string.together_sync_synced
            TogetherSyncStatus.SYNCING -> R.string.together_sync_syncing
            TogetherSyncStatus.WAITING_FOR_HOST -> R.string.together_sync_waiting
            TogetherSyncStatus.LOCAL_TRACK -> R.string.together_local_not_shareable
            TogetherSyncStatus.CONNECTING -> R.string.together_sync_connecting
        },
    )
    val pulse = if (status == TogetherSyncStatus.SYNCING || status == TogetherSyncStatus.CONNECTING) {
        rememberInfiniteTransition(label = "Together sync dot").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(730), RepeatMode.Reverse),
            label = "Together sync dot opacity",
        )
    } else null

    Surface(modifier = modifier, shape = CircleShape, color = colors.surfaceContainerHighest) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(6.dp)
                    .graphicsLayer { alpha = pulse?.value ?: 1f }
                    .background(dotColor, CircleShape),
            )
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
