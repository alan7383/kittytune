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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val dismissThreshold = with(density) { 56.dp.toPx() }
        val minimumFlingVelocity = with(density) { 200.dp.toPx() }
        val dismiss by rememberUpdatedState(onDismissRequest)
        var expandedOffset by remember(sheetState, configuration) { mutableFloatStateOf(Float.NaN) }

        LaunchedEffect(sheetState, configuration) {
            expandedOffset = snapshotFlow {
                if (sheetState.currentValue == SheetValue.Expanded && !sheetState.isAnimationRunning) {
                    runCatching { sheetState.requireOffset() }.getOrNull()
                } else {
                    null
                }
            }.filterNotNull().first()
        }

        val dismissNestedScrollConnection = remember(sheetState, configuration, density) {
            object : NestedScrollConnection {
                override suspend fun onPreFling(available: Velocity): Velocity {
                    // Material 3 consumes the reversal in pre-scroll before the list sees it.
                    // Measure the distance from the expanded position at release instead of summing scroll deltas.
                    if (expandedOffset.isNaN() || available.y < 0f) return Velocity.Zero
                    val displacement = sheetState.requireOffset() - expandedOffset
                    if (displacement > dismissThreshold ||
                        (displacement > 15f && available.y > minimumFlingVelocity)
                    ) {
                        sheetState.hide()
                        if (!sheetState.isVisible) {
                            dismiss()
                            return available
                        }
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
