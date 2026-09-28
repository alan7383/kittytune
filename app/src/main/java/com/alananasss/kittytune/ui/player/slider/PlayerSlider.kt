package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.PlayerSliderStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    sliderStyle: PlayerSliderStyle,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: SliderColors = SliderDefaults.colors(),
    enabled: Boolean = true,
    bufferedValue: Float? = null,
) {
    val clampedValue = if (valueRange.endInclusive > valueRange.start) {
        value.coerceIn(valueRange.start, valueRange.endInclusive)
    } else {
        0f
    }

    when (sliderStyle) {
        PlayerSliderStyle.BAR -> {
            Slider(
                value = clampedValue,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                colors = colors,
                enabled = enabled,
                modifier = modifier
            )
        }
        PlayerSliderStyle.WAVY -> {
            WavySlider(
                value = clampedValue,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = colors,
                isPlaying = isPlaying,
                enabled = enabled,
                bufferedValue = bufferedValue,
                modifier = modifier
            )
        }
        PlayerSliderStyle.SLIM -> {
            val trackInteractionSource = remember { MutableInteractionSource() }
            val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
            val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
            val isTrackActive = isTrackDragged || isTrackPressed

            val trackHeight by animateDpAsState(
                targetValue = if (isTrackActive) 16.dp else 10.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "trackHeight"
            )

            val sliderState = remember(valueRange) { androidx.compose.material3.SliderState(clampedValue, 0, valueRange) }
            androidx.compose.runtime.LaunchedEffect(clampedValue) {
                sliderState.value = clampedValue
            }

            Slider(
                state = sliderState,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                interactionSource = trackInteractionSource,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { state ->
                    PlayerSliderTrack(
                        sliderState = state,
                        trackHeight = trackHeight,
                        colors = colors,
                        bufferedValue = bufferedValue
                    )
                },
                colors = colors,
                enabled = enabled,
                modifier = modifier
            )
        }
        PlayerSliderStyle.SQUIGGLY -> {
            SquigglySlider(
                value = clampedValue,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = colors,
                isPlaying = isPlaying,
                enabled = enabled,
                bufferedValue = bufferedValue,
                modifier = modifier
            )
        }
    }
}
