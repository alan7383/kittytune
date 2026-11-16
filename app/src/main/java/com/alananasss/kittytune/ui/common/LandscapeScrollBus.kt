package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

// Shared holder for the scrollable state of the screen that is currently displayed
// next to the landscape navigation rail.
//
// The rail itself is static (it always fits on screen), so vertical swipes over the
// menu area used to do nothing. KittyNavigationRail now forwards vertical drags to
// whatever [ScrollableState] is registered here, which makes a swipe up/down on the
// left menu (Home / Search / etc.) scroll the page beside it.
//
// Screens register their primary list state via [RegisterLandscapeScrollable]; the
// registration is cleared automatically when the screen leaves the composition.
object LandscapeScrollBus {
    @Volatile
    var activeScrollable: ScrollableState? = null
}

// Registers [state] as the current scroll target for the landscape rail gestures.
// Pass the main LazyListState / LazyGridState of a tab screen (Home, Library, Queue,
// Genres...). Registration only lives as long as this composable is in the
// composition, so navigating between tabs always leaves the freshest state behind.
@Composable
fun RegisterLandscapeScrollable(state: ScrollableState?) {
    DisposableEffect(state) {
        if (state != null) {
            LandscapeScrollBus.activeScrollable = state
        }
        onDispose {
            if (LandscapeScrollBus.activeScrollable === state) {
                LandscapeScrollBus.activeScrollable = null
            }
        }
    }
}
