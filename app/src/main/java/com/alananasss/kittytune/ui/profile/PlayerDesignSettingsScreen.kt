package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerDesign
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.player.slider.SliderStyleDialog

/**
 * The Player sub-page: which design the player is, and how it behaves.
 *
 * The desktop splits this out of its Interface page; on Android the same rows were buried in the
 * Appearance screen, between the theme and the app icon, which is a long way from anything a player
 * setting has to do with a colour.
 */
@Composable
fun PlayerDesignSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var playerDesign by remember { mutableStateOf(prefs.getPlayerDesign()) }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }
    var showDesignDialog by remember { mutableStateOf(false) }
    var showSliderDialog by remember { mutableStateOf(false) }

    if (showDesignDialog) {
        AlertDialog(
            onDismissRequest = { showDesignDialog = false },
            title = { Text(stringResource(R.string.pref_player_design)) },
            text = {
                Column {
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_pixel),
                        description = stringResource(R.string.player_design_pixel_desc),
                        design = PlayerDesign.PIXEL_PLAYER,
                        selected = playerDesign,
                        onSelect = { playerDesign = it; prefs.setPlayerDesign(it); showDesignDialog = false }
                    )
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_soundcloud),
                        description = stringResource(R.string.player_design_soundcloud_desc),
                        design = PlayerDesign.SOUNDCLOUD,
                        selected = playerDesign,
                        onSelect = { playerDesign = it; prefs.setPlayerDesign(it); showDesignDialog = false }
                    )
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_modern),
                        description = stringResource(R.string.player_design_modern_desc),
                        design = PlayerDesign.MODERN,
                        selected = playerDesign,
                        onSelect = { playerDesign = it; prefs.setPlayerDesign(it); showDesignDialog = false }
                    )
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_classic),
                        description = stringResource(R.string.player_design_classic_desc),
                        design = PlayerDesign.CLASSIC,
                        selected = playerDesign,
                        onSelect = { playerDesign = it; prefs.setPlayerDesign(it); showDesignDialog = false }
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (showSliderDialog) {
        SliderStyleDialog(
            currentStyle = sliderStyle,
            onStyleSelected = {
                sliderStyle = it
                prefs.setPlayerSliderStyle(it)
            },
            onDismiss = { showSliderDialog = false }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_page_player),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_page_player),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_player_design),
                                subtitle = when (playerDesign) {
                                    PlayerDesign.PIXEL_PLAYER -> stringResource(R.string.player_design_pixel)
                                    PlayerDesign.SOUNDCLOUD -> stringResource(R.string.player_design_soundcloud)
                                    PlayerDesign.MODERN -> stringResource(R.string.player_design_modern)
                                    PlayerDesign.CLASSIC -> stringResource(R.string.player_design_classic)
                                },
                                icon = Icons.Rounded.Palette,
                                onClick = { showDesignDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_slider_style),
                                subtitle = sliderStyleLabel(sliderStyle),
                                icon = Icons.Rounded.Tune,
                                onClick = { showSliderDialog = true }
                            )
                        },
                    )
                )
            }

        }
    }
}

@Composable
private fun sliderStyleLabel(style: PlayerSliderStyle): String = when (style) {
    PlayerSliderStyle.BAR -> stringResource(R.string.slider_style_bar)
    PlayerSliderStyle.WAVY -> stringResource(R.string.slider_style_wavy)
    PlayerSliderStyle.SLIM -> stringResource(R.string.slider_style_slim)
    PlayerSliderStyle.SQUIGGLY -> stringResource(R.string.slider_style_squiggly)
}
