package com.alananasss.kittytune.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun KittyModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val isPhoneLandscape = windowSizeInfo.showNavRail

    if (isPhoneLandscape) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val offScreenOffset = with(density) { 450.dp.toPx() }
        val offsetAnim = remember { Animatable(offScreenOffset) }
        val scrimAlpha = remember { Animatable(0f) }
        var isDismissing by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            launch { scrimAlpha.animateTo(1f, animationSpec = tween(200, easing = LinearOutSlowInEasing)) }
            launch { offsetAnim.animateTo(0f, animationSpec = tween(250, easing = FastOutSlowInEasing)) }
        }

        val dismiss: () -> Unit = {
            if (!isDismissing) {
                isDismissing = true
                scope.launch {
                    coroutineScope {
                        launch { scrimAlpha.animateTo(0f, animationSpec = tween(180, easing = FastOutLinearInEasing)) }
                        launch { offsetAnim.animateTo(offScreenOffset, animationSpec = tween(180, easing = FastOutLinearInEasing)) }
                    }
                    onDismissRequest()
                }
            }
        }

        Dialog(
            onDismissRequest = { dismiss() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * scrimAlpha.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismiss() }
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 320.dp, max = 400.dp)
                        .fillMaxWidth(0.5f)
                        .offset { IntOffset(offsetAnim.value.roundToInt().coerceAtLeast(0), 0) }
                        .align(Alignment.CenterEnd)
                        .draggable(
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    val newOffset = (offsetAnim.value + delta).coerceAtLeast(0f)
                                    offsetAnim.snapTo(newOffset)
                                    val dragFraction = (1f - (newOffset / offScreenOffset)).coerceIn(0f, 1f)
                                    scrimAlpha.snapTo(dragFraction)
                                }
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                if (offsetAnim.value > 120f) {
                                    dismiss()
                                } else {
                                    scope.launch {
                                        launch { scrimAlpha.animateTo(1f, animationSpec = tween(150)) }
                                        launch { offsetAnim.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                }
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .windowInsetsPadding(WindowInsets.systemBars),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    tonalElevation = tonalElevation
                ) {
                    Column(
                        modifier = modifier,
                        content = content
                    )
                }
            }
        }
    } else {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val dismissThreshold = with(density) { 56.dp.toPx() }
        val minFlingDismissVelocity = with(density) { 200.dp.toPx() }

        var accumulatedDownwardDrag by remember { mutableFloatStateOf(0f) }
        var expandedOffset by remember { mutableFloatStateOf(Float.NaN) }

        LaunchedEffect(sheetState.currentValue) {
            if (sheetState.currentValue == SheetValue.Expanded) {
                try {
                    expandedOffset = sheetState.requireOffset()
                } catch (_: Exception) {}
            }
        }

        val dismissNestedScrollConnection = remember(sheetState, onDismissRequest) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        if (available.y > 0f) {
                            accumulatedDownwardDrag += available.y
                        } else if (available.y < 0f) {
                            accumulatedDownwardDrag = (accumulatedDownwardDrag + available.y).coerceAtLeast(0f)
                        }
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val currentOffset = try {
                        sheetState.requireOffset()
                    } catch (_: Exception) {
                        Float.NaN
                    }
                    val draggedOffset = if (!currentOffset.isNaN() && !expandedOffset.isNaN()) {
                        (currentOffset - expandedOffset).coerceAtLeast(0f)
                    } else 0f

                    val totalDrag = max(accumulatedDownwardDrag, draggedOffset)
                    accumulatedDownwardDrag = 0f

                    if (totalDrag > dismissThreshold || (totalDrag > 15f && available.y > minFlingDismissVelocity)) {
                        scope.launch {
                            try {
                                sheetState.hide()
                            } finally {
                                onDismissRequest()
                            }
                        }
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        @Suppress("DEPRECATION")
        CompositionLocalProvider(
            androidx.compose.foundation.LocalOverscrollFactory provides null,
            androidx.compose.foundation.LocalOverscrollConfiguration provides null
        ) {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                sheetState = sheetState,
                shape = shape,
                containerColor = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
                scrimColor = scrimColor,
                dragHandle = dragHandle,
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nestedScroll(dismissNestedScrollConnection),
                        content = content
                    )
                }
            )
        }
    }
}
