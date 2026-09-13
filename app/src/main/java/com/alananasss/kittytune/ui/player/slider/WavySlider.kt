package com.alananasss.kittytune.ui.player.slider

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.player.pixel.WavySliderExpressive

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
    enabled: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
    bufferedValue: Float? = null,
) {
    WavySliderExpressive(
        value = { value },
        onValueChange = onValueChange,
        onValueCommit = onValueCommit ?: onValueChangeFinished?.let { finish -> { _: Float -> finish() } },
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        activeTrackColor = colors.activeTrackColor,
        inactiveTrackColor = colors.inactiveTrackColor,
        thumbColor = colors.thumbColor,
        isPlaying = isPlaying,
        strokeWidth = strokeWidth,
        thumbRadius = thumbRadius,
        wavelength = wavelength,
        waveSpeed = waveSpeed
    )
}
