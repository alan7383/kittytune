package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class QueueDrawerIssue86EmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val queueOpen = mutableStateOf(true)

    @Test fun drawerStaysOpenWhenTheSameDragReturnsNearTheExpandedPosition() {
        openQueue()
        val initialY = headerY()
        pullDrawerDown()
        assertTrue("The first part of the gesture must move the drawer", headerY() > initialY + 50f)

        queueHeader().performTouchInput {
            repeat(10) { moveBy(Offset(0f, -12.dp.toPx()), delayMillis = 20) }
            advanceEventTime(160)
        }
        assertTrue("The reversal must bring the drawer back near its open position", abs(headerY() - initialY) < 50f)
        queueHeader().performTouchInput { up() }

        compose.waitForIdle()
        queueHeader().assertIsDisplayed()
        assertTrue("The drawer should settle at its expanded position", abs(headerY() - initialY) < 2f)
    }

    @Test fun reversingPastTheExpandedPositionKeepsScrollingTheQueueOpen() {
        openQueue()
        val initialY = headerY()
        pullDrawerDown()
        assertTrue(headerY() > initialY + 50f)

        queueHeader().performTouchInput {
            repeat(18) { moveBy(Offset(0f, -15.dp.toPx()), delayMillis = 16) }
            up()
        }

        compose.waitForIdle()
        queueHeader().assertIsDisplayed()
        assertTrue(abs(headerY() - initialY) < 2f)
        compose.onNodeWithText(trackTitle(0)).assertDoesNotExist()
    }

    @Test fun releasingWhileTheDrawerIsPulledDownStillDismissesIt() {
        openQueue()
        val initialY = headerY()
        pullDrawerDown()
        assertTrue(headerY() > initialY + 50f)
        queueHeader().performTouchInput {
            repeat(6) { moveBy(Offset(0f, 15.dp.toPx()), delayMillis = 20) }
            advanceEventTime(160)
            up()
        }
        compose.waitUntil(5_000) { !queueOpen.value }
        queueHeader().assertDoesNotExist()
    }

    private fun queueHeader() = compose.onNodeWithText(compose.activity.getString(R.string.player_queue))

    private fun headerY() = queueHeader().fetchSemanticsNode().boundsInRoot.top

    private fun pullDrawerDown() {
        compose.onNodeWithText(trackTitle(0)).performTouchInput {
            down(center)
            repeat(9) { moveBy(Offset(0f, 15.dp.toPx()), delayMillis = 20) }
        }
    }

    private fun trackTitle(index: Int) = "Queue gesture track ${index + 1}"

    @OptIn(ExperimentalMaterial3Api::class)
    private fun openQueue() {
        val activity = compose.activity
        compose.runOnUiThread {
            val vm = ViewModelProvider(activity)[PlayerViewModel::class.java]
            val tracks = List(30) { index ->
                Track(-86_000L - index, trackTitle(index), null, 90_000, User(-86L, "Queue test", null))
            }
            vm.uiQueueState = tracks
            vm.uiCurrentQueueIndex = 0
            vm.uiCurrentTrack = tracks.first()
            activity.setContent {
                MaterialTheme {
                    if (queueOpen.value) {
                        KittyModalBottomSheet(
                            onDismissRequest = { queueOpen.value = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            QueueContent(vm, isQueueOpen = true, onCloseQueue = { queueOpen.value = false }, onOpenExpandedQueue = {})
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
        compose.waitUntil(12_000) { compose.onNodeWithText(trackTitle(0)).isDisplayed() }
        compose.waitForIdle()
    }
}
