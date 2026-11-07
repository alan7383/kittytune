package com.alananasss.kittytune.ui.library

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.ui.common.SwipeToDeleteItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** The playlist's gesture nesting: the reorder handle lives inside the swipe container. */
@RunWith(AndroidJUnit4::class)
class PlaylistReorderIssue79Test {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun reorderWithSwipeAndMenu() = reorderAndRemove(swipeEnabled = true)

    @Test fun reorderWithMenuOnly() = reorderAndRemove(swipeEnabled = false)

    private fun reorderAndRemove(swipeEnabled: Boolean) {
        val tracks = mutableStateListOf(1, 2, 3, 4)
        var dragging = false
        var disposals = 0
        var deletions = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    val listState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                        val fromIndex = tracks.indexOf(from.key)
                        val toIndex = tracks.indexOf(to.key)
                        tracks.add(toIndex, tracks.removeAt(fromIndex))
                    }
                    val anyDragging = reorderState.isAnyItemDragging
                    SideEffect { dragging = anyDragging }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(tracks, key = { it }) { track ->
                            ReorderableItem(reorderState, key = track) { isDragging ->
                                SwipeToDeleteItem(
                                    key = track,
                                    enabled = swipeEnabled && !isDragging,
                                    clipContent = !isDragging,
                                    horizontalPadding = 8.dp,
                                    endZoneIgnoreWidth = 104.dp,
                                    onDelete = { deletions++; tracks.remove(track) }
                                ) {
                                    DisposableEffect(track) { onDispose { disposals++ } }
                                    Row(
                                        Modifier.fillMaxWidth().height(88.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Track $track", Modifier.weight(1f).testTag("body-$track"))
                                        Box(Modifier.size(48.dp).testTag("handle-$track").draggableHandle())
                                        Box(Modifier.size(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val handle = compose.onNodeWithTag("handle-1")
        handle.performTouchInput { down(center); moveBy(Offset(0f, 20.dp.toPx())) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("The handle must keep its active gesture after swipe is disabled", dragging)
            assertEquals("Starting a drag must preserve the row composition", 0, disposals)
        }
        handle.performTouchInput {
            moveBy(Offset(0f, 150.dp.toPx()), delayMillis = 300)
        }
        compose.waitForIdle()
        handle.performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(dragging)
            assertTrue("The first track should move down", tracks.indexOf(1) > 0)
            assertEquals(0, disposals)
            assertEquals("Reordering must not remove a track", 0, deletions)
        }
        compose.onNodeWithTag("body-1").performTouchInput {
            swipe(start = centerLeft + Offset(10f, 0f), end = centerRight, durationMillis = 400)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(if (swipeEnabled) 1 else 0, deletions)
            assertEquals(!swipeEnabled, tracks.contains(1))
        }
    }
}
