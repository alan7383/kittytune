package com.alananasss.kittytune.ui.liquidglass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidSlider
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidToggle
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the gesture arbitration between the liquid glass controls and a
 * scrollable ancestor. A vertical drag that starts on a control must stay with the control:
 * it must not scroll the page (and, in the player, must not trigger swipe-to-dismiss).
 */
@RunWith(AndroidJUnit4::class)
class LiquidGlassGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val initialScroll = 200

    @Test
    fun verticalDragOnSliderDoesNotScrollParent() {
        var value by mutableFloatStateOf(0.5f)
        lateinit var scrollState: ScrollState

        composeRule.setContent {
            val backdrop = rememberCanvasBackdrop { drawRect(Color.Blue) }
            val state = rememberScrollState(initial = initialScroll)
            scrollState = state
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(state)
                ) {
                    Spacer(Modifier.height(600.dp))
                    Box(Modifier.width(300.dp).testTag("slider")) {
                        KittyLiquidSlider(
                            value = { value },
                            onValueChange = { value = it },
                            backdrop = backdrop
                        )
                    }
                    Spacer(Modifier.height(1200.dp))
                }
            }
        }

        composeRule.onNodeWithTag("slider").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -300f),
                durationMillis = 500
            )
        }

        composeRule.runOnIdle {
            assertEquals(initialScroll, scrollState.value)
        }
    }

    @Test
    fun verticalDragOnToggleDoesNotScrollParent() {
        var checked by mutableStateOf(false)
        lateinit var scrollState: ScrollState

        composeRule.setContent {
            val backdrop = rememberCanvasBackdrop { drawRect(Color.Blue) }
            val state = rememberScrollState(initial = initialScroll)
            scrollState = state
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(state)
                ) {
                    Spacer(Modifier.height(600.dp))
                    Box(Modifier.testTag("toggle")) {
                        KittyLiquidToggle(
                            selected = { checked },
                            onSelect = { checked = it },
                            backdrop = backdrop
                        )
                    }
                    Spacer(Modifier.height(1200.dp))
                }
            }
        }

        composeRule.onNodeWithTag("toggle").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -300f),
                durationMillis = 500
            )
        }

        composeRule.runOnIdle {
            assertEquals(initialScroll, scrollState.value)
        }
    }

    @Test
    fun verticalDragOnEmptyAreaStillScrolls() {
        lateinit var scrollState: ScrollState

        composeRule.setContent {
            val state = rememberScrollState(initial = initialScroll)
            scrollState = state
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(state)
                        .testTag("scroller")
                ) {
                    Spacer(Modifier.height(600.dp))
                    Spacer(Modifier.height(1200.dp))
                }
            }
        }

        composeRule.onNodeWithTag("scroller").performTouchInput {
            swipe(
                start = Offset(centerX, centerY + 100f),
                end = Offset(centerX, centerY - 200f),
                durationMillis = 500
            )
        }

        composeRule.runOnIdle {
            assertTrue(scrollState.value > initialScroll)
        }
    }
}
