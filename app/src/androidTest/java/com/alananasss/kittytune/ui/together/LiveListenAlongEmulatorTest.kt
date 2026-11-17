package com.alananasss.kittytune.ui.together

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.ui.player.PlayerViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveListenAlongEmulatorTest {
    @get:Rule val compose = createEmptyComposeRule()

    @After
    fun tearDown() {
        Together.stopListening()
        Together.setHostLocked(false)
    }

    @Test
    fun liveListenSheetStartsBroadcastTogglesLockAndRotatesInviteLink() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        Together.stopListening()
        Together.setHostLocked(false)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var openedRoomCode: String? = null
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[PlayerViewModel::class.java]
                activity.setContent {
                    MaterialTheme {
                        LiveListenSheet(
                            playerViewModel = vm,
                            onOpenRoom = { openedRoomCode = it },
                            onDismissRequest = {}
                        )
                    }
                }
            }

            val startLabel = targetContext.getString(R.string.together_start_live)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(startLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(startLabel).performClick()

            compose.waitUntil(5_000) { Together.active.value != null }
            val initialCode = Together.active.value
            assertNotNull(initialCode)
            assertTrue(Together.isHostOf(initialCode!!))

            val formattedInitial = TogetherWire.displayCode(initialCode)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(formattedInitial).fetchSemanticsNodes().isNotEmpty() }

            // Toggle Listen-only mode ON and verify state updates
            val lockLabel = targetContext.getString(R.string.together_lock_controls)
            assertTrue(compose.onAllNodesWithText(lockLabel).fetchSemanticsNodes().isNotEmpty())
            Together.setHostLocked(true)
            assertTrue(Together.isHostLocked.value)

            // Reset invite link rotates the active live room code
            val resetLabel = targetContext.getString(R.string.together_reset_link)
            compose.onNodeWithText(resetLabel).performClick()
            compose.waitUntil(5_000) { Together.active.value != null && Together.active.value != initialCode }
            val rotatedCode = Together.active.value!!
            assertNotEquals(initialCode, rotatedCode)
            assertTrue(Together.isHostOf(rotatedCode))

            // Open room callback navigates with the active rotated code
            val openRoomLabel = targetContext.getString(R.string.together_open_room)
            compose.onNodeWithText(openRoomLabel).performClick()
            compose.waitForIdle()
            assertEquals(rotatedCode, openedRoomCode)

            Together.forget(rotatedCode)
            scenario.onActivity { it.setContent { Box {} } }
        }
    }

    @Test
    fun deepLinkIntentShowsInvitePromptThenJoinsTogetherRoomWithoutStealingHost() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PlayerPreferences(targetContext)
        val tokenManager = com.alananasss.kittytune.data.TokenManager(targetContext)
        val wasSetupCompleted = prefs.isSetupCompleted()
        val wasGuest = tokenManager.isGuestMode()
        prefs.setSetupCompleted(true)
        tokenManager.setGuestMode(true)

        val remoteCode = "R4S6T8V2"
        Together.forget(remoteCode)

        try {
            val inviteUri = Uri.parse(TogetherLinks.appDeepLink(remoteCode, autoJoin = false))
            assertEquals(remoteCode, TogetherLinks.extractCodeFromUri(inviteUri))
            assertFalse(TogetherLinks.isAutoJoinUri(inviteUri))

            val autoJoinUri = Uri.parse(TogetherLinks.appDeepLink(remoteCode, autoJoin = true))
            assertTrue(TogetherLinks.isAutoJoinUri(autoJoinUri))

            val launchIntent = Intent(Intent.ACTION_VIEW, inviteUri).apply {
                setClass(targetContext, MainActivity::class.java)
            }

            ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
                val listenLabel = targetContext.getString(R.string.together_listen)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(listenLabel).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(listenLabel).performClick()

                compose.waitUntil(10_000) { Together.active.value == remoteCode }
                assertEquals(remoteCode, Together.active.value)
                // Joining via invite link must wait for the real host's State instead of stealing host!
                assertFalse(Together.isHostOf(remoteCode))

                val formattedCode = TogetherWire.displayCode(remoteCode)
                compose.waitUntil(10_000) { compose.onAllNodesWithText(formattedCode).fetchSemanticsNodes().isNotEmpty() }

                Together.forget(remoteCode)
                scenario.onActivity { it.setContent { Box {} } }
            }
        } finally {
            Together.forget(remoteCode)
            prefs.setSetupCompleted(wasSetupCompleted)
            tokenManager.setGuestMode(wasGuest)
        }
    }
}
