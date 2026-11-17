package com.alananasss.kittytune.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import com.alananasss.kittytune.wear.data.PhoneWearableController
import com.alananasss.kittytune.wear.presentation.theme.KittyTuneWearTheme
import kotlinx.coroutines.launch

/**
 * Entry Activity for the KittyTune Wear OS companion app.
 * Configured as a standalone launcher activity.
 *
 * Hosts the Wear Material 3 [AppScaffold] containing [PlaybackScreen].
 */
class MainActivity : ComponentActivity() {

    private lateinit var controller: PhoneWearableController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controller = PhoneWearableController(applicationContext, lifecycleScope)
        val viewModelFactory = PlaybackViewModel.provideFactory(controller)

        setContent {
            KittyTuneWearTheme {
                AppScaffold {
                    val playbackViewModel: PlaybackViewModel = viewModel(factory = viewModelFactory)
                    // PlaybackScreen manages ScreenScaffold, TimeText, rotary volume, and circular layout
                    PlaybackScreen(viewModel = playbackViewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.setForeground(true)
    }

    override fun onStop() {
        controller.setForeground(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // The Data Layer only pushes on change, so ask for a fresh frame whenever the screen comes
        // back: after a phone call, after ambient, or after the phone edited the queue on its own.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleScope.launch { controller.requestSync() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.cleanup()
    }
}
