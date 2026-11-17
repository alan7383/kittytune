package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.data.LyricsCache
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.lyrics.LyricsScreen
import com.alananasss.kittytune.ui.player.lyrics.SearchLyricsView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsIssue85EmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun sailingRanksTheMatchingRecordingFirst() {
        val instrumental = result("10286", "Unison", "Sailing", 228.0, "[Instrumental Only]").copy(noLyrics = true, instrumental = true)
        val wrong = result("2", "SIMPMUSIC", "Sailing", 180.0, "[00:10.00]Wrong lyrics", lineSync = true)
        val target = LyricsMatcher.Target("Sailing", "Alan Walker", 228_000)
        assertEquals(instrumental, ManualLyricsSearch.merge(listOf(wrong), listOf(instrumental), target).first())
    }

    @Test fun selectingOneSimpMusicResultMarksOnlyThatResultActive() {
        val first = result("1", "SIMPMUSIC", "Fake A Smile", 170.0, "First lyrics")
        val remix = result("2", "SIMPMUSIC", "Fake A Smile (Remix)", 180.0, "Remix lyrics")
        val vm = showResults(first, remix)
        compose.onNodeWithText(remix.name).performClick()
        compose.waitUntil(5_000) { !vm.isLyricsLoading && vm.rawPlainLyrics == remix.rawContent }
        compose.onAllNodesWithText("Active").assertCountEquals(1)
        compose.onNode(hasText(remix.name) and hasText("Active")).assertIsDisplayed()
    }

    @Test fun selectingMusixmatchWithoutLyricsKeepsTheSourceAndHidesTheEmptyStateButton() {
        val pick = result("3", "MUSIXMATCH", "Sailing", 228.0, "").copy(noLyrics = true)
        val vm = showResults(pick)
        compose.onNodeWithText(pick.name).performClick()
        compose.waitUntil(5_000) { !vm.isLyricsLoading }
        compose.onAllNodesWithText("Active").assertCountEquals(1)
        compose.runOnIdle {
            compose.activity.setContent { MaterialTheme { LyricsScreen(vm, onClose = {}) } }
        }
        val context = compose.activity
        compose.onNodeWithText(context.getString(R.string.lyrics_no_data)).assertIsDisplayed()
        val searchLabel = context.getString(R.string.lyrics_manual_search)
        compose.onNodeWithText(searchLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(searchLabel).performClick()
        compose.onNode(hasText(pick.name) and hasText("Active")).assertIsDisplayed()
    }

    @Test fun lateWordTimingUpdatesTheDisplayedSourceTogetherWithTheLyrics() {
        val lineTimed = result("1", "SIMPMUSIC", "Fake A Smile", 168.0,
            "[00:01.00]First line\n[00:02.00]Second line", lineSync = true)
        val rich = result("2", "MUSIXMATCH", "Fake A Smile", 168.0,
            "[00:01.00]<00:01.00>Word <00:01.60>timing\n[00:03.00]Another line",
            lineSync = true).copy(hasWordSync = true)
        val vm = showResults(lineTimed)
        compose.runOnIdle { vm.selectUnifiedLyricResult(lineTimed, isManualPick = false) }
        compose.waitUntil(5_000) { !vm.isLyricsLoading && vm.currentLyricsSource == lineTimed.provider }
        compose.runOnIdle {
            vm.unifiedLyricSearchResults.add(0, rich)
            vm.upgradeToSyncedLyrics(vm.currentTrack!!, LyricsMatcher.Target(rich.name, rich.artistName, 168_000))
        }
        compose.waitUntil(5_000) { !vm.isLyricsLoading && vm.currentLyricsSource == rich.provider }
        compose.runOnIdle { assertTrue(vm.lyricsLines.any { it.words.size > 1 }) }
        val sourceLabel = compose.activity.getString(R.string.lyrics_search_now_showing, "Musixmatch")
        compose.onNodeWithText(sourceLabel, substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Active").assertCountEquals(1)
        compose.onNode(hasText("Musixmatch") and hasText("Active")).assertIsDisplayed()

        compose.onNode(hasText("SimpMusic") and hasText(lineTimed.name)).performClick()
        compose.waitUntil(5_000) { !vm.isLyricsLoading && vm.currentLyricsSource == lineTimed.provider }
        compose.runOnIdle {
            vm.upgradeToSyncedLyrics(vm.currentTrack!!, LyricsMatcher.Target(rich.name, rich.artistName, 168_000))
            assertEquals(lineTimed.provider, vm.currentLyricsSource)
            assertTrue(vm.lyricsLines.none { it.words.size > 1 })
        }
    }

    @Test fun sailingResolvesAsInstrumentalWithLiveProviders() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveLyrics85") == "true")
        val vm = showResults(result("fixture", "UNISON", "Sailing", 228.0, ""))
        LyricsCache.invalidate(85_000L)
        try {
            compose.runOnIdle { vm.reloadLyrics() }
            compose.waitUntil(35_000) { !vm.isLyricsLoading }
            compose.runOnIdle {
                assertTrue("Search settled on ${vm.currentLyricsSource}", vm.hasSelectedResultWithoutLyrics)
                assertTrue(vm.lyricsLines.isEmpty())
                assertTrue(vm.rawPlainLyrics.isNullOrBlank())
            }
        } finally {
            LyricsCache.invalidate(85_000L)
        }
    }

    private fun showResults(vararg results: UnifiedLyricResult): PlayerViewModel {
        lateinit var vm: PlayerViewModel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
            val first = results.first()
            vm.currentTrack = Track(85_000L, first.name, null, (first.durationSec * 1000).toLong(), User(-85, "Alan Walker", null))
            vm.unifiedLyricSearchResults.clear()
            vm.unifiedLyricSearchResults.addAll(results)
            compose.activity.setContent {
                MaterialTheme { SearchLyricsView(vm, onCloseSearch = {}, isThemeBg = true) }
            }
        }
        return vm
    }

    private fun result(
        id: String, provider: String, name: String, duration: Double, raw: String, lineSync: Boolean = false,
    ) = UnifiedLyricResult(id, name, "Alan Walker", null, duration, lineSync, false, provider, raw)
}
