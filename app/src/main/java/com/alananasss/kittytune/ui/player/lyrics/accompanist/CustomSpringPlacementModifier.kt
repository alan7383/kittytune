@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ApproachLayoutModifierNode
import androidx.compose.ui.layout.ApproachMeasureScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_SPRING_DISPLACEMENT_PX = 600

class CustomSpringPlacementModifierNode(
    var lookaheadScope: LookaheadScope,
    var itemKey: Any,
    var isManualScrolling: Boolean,
    var stiffness: Float
) : ApproachLayoutModifierNode, Modifier.Node() {
    private var offsetAnimatable: Animatable<IntOffset, AnimationVector2D>? = null
    private var isInitialized = false

    override fun isMeasurementApproachInProgress(lookaheadSize: IntSize): Boolean = false

    override fun Placeable.PlacementScope.isPlacementApproachInProgress(
        lookaheadCoordinates: LayoutCoordinates
    ): Boolean {
        val anim = offsetAnimatable ?: return false
        if (!isInitialized || isManualScrolling) return false
        val target = with(lookaheadScope) {
            lookaheadScopeCoordinates.localLookaheadPositionOf(lookaheadCoordinates).round()
        }
        return anim.isRunning || anim.value != target
    }

    override fun ApproachMeasureScope.approachMeasure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            val coordinates = coordinates
            if (coordinates != null) {
                val target = with(lookaheadScope) {
                    lookaheadScopeCoordinates.localLookaheadPositionOf(coordinates).round()
                }
                val placementOffset = with(lookaheadScope) {
                    lookaheadScopeCoordinates.localPositionOf(coordinates, Offset.Zero).round()
                }

                var anim = offsetAnimatable
                if (anim == null || !isInitialized || isManualScrolling) {
                    offsetAnimatable = Animatable(target, IntOffset.VectorConverter)
                    isInitialized = true
                    placeable.place(0, 0)
                    return@layout
                }

                val distanceY = abs(target.y - anim.value.y)
                val distanceX = abs(target.x - anim.value.x)
                if (distanceY > MAX_SPRING_DISPLACEMENT_PX || distanceX > MAX_SPRING_DISPLACEMENT_PX) {
                    offsetAnimatable = Animatable(target, IntOffset.VectorConverter)
                    placeable.place(0, 0)
                    return@layout
                }

                if (anim.targetValue != target) {
                    coroutineScope.launch {
                        anim.animateTo(
                            target,
                            spring(dampingRatio = 0.95f, stiffness = stiffness)
                        )
                    }
                }

                val animatedOffset = anim.value
                val delta = animatedOffset - placementOffset

                // Safety guard: delta must never place a line into another line's slot or across the screen
                if (abs(delta.y) > MAX_SPRING_DISPLACEMENT_PX || abs(delta.x) > MAX_SPRING_DISPLACEMENT_PX) {
                    placeable.place(0, 0)
                } else {
                    placeable.place(delta.x, delta.y)
                }
            } else {
                placeable.place(0, 0)
            }
        }
    }

    fun updateState(newScope: LookaheadScope, newKey: Any, newIsManualScrolling: Boolean, newStiffness: Float) {
        lookaheadScope = newScope
        isManualScrolling = newIsManualScrolling
        stiffness = newStiffness
        if (itemKey != newKey) {
            itemKey = newKey
            offsetAnimatable = null
            isInitialized = false
        }
    }
}

data class CustomSpringPlacementNodeElement(
    val lookaheadScope: LookaheadScope,
    val itemKey: Any,
    val isManualScrolling: Boolean,
    val stiffness: Float
) : ModifierNodeElement<CustomSpringPlacementModifierNode>() {
    override fun update(node: CustomSpringPlacementModifierNode) {
        node.updateState(lookaheadScope, itemKey, isManualScrolling, stiffness)
    }
    override fun create(): CustomSpringPlacementModifierNode =
        CustomSpringPlacementModifierNode(lookaheadScope, itemKey, isManualScrolling, stiffness)
}

fun Modifier.springPlacement(
    lookaheadScope: LookaheadScope,
    itemKey: Any,
    isManualScrolling: Boolean,
    stiffness: Float
): Modifier = this.then(CustomSpringPlacementNodeElement(lookaheadScope, itemKey, isManualScrolling, stiffness))
