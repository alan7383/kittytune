package com.alananasss.kittytune.ui.player.audio

import androidx.compose.runtime.Composable
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * Backwards compatibility wrapper: delegates to the full screen [EqualizerScreen].
 */
@Deprecated("Equalizer has been migrated to a full screen. Use EqualizerScreen instead.")
@Composable
fun EqualizerSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    EqualizerScreen(
        viewModel = viewModel,
        onBackClick = onDismiss
    )
}
