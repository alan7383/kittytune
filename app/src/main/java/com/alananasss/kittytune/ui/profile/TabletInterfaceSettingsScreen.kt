package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.TabletNavPlacement
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.hapticAction

@Composable
fun TabletInterfaceSettingsScreen(isTablet: Boolean, onBackClick: () -> Unit) {
    if (!isTablet) {
        LaunchedEffect(Unit) { onBackClick() }
        return
    }

    val context = LocalContext.current
    val prefs = remember(context) { PlayerPreferences(context) }
    val placement by remember(prefs) { prefs.tabletNavPlacementFlow() }
        .collectAsState(initial = prefs.getTabletNavPlacement())
    val sidePlayer by remember(prefs) { prefs.tabletSidePlayerFlow() }
        .collectAsState(initial = prefs.getTabletSidePlayerEnabled())
    val hideLabels by remember(prefs) { prefs.navHideLabelsFlow() }
        .collectAsState(initial = prefs.getNavHideLabels())
    val layouts = listOf(
        TabletNavPlacement.CLASSIC_DOCK to R.string.pref_tablet_nav_classic_dock,
        TabletNavPlacement.BOTTOM to R.string.pref_tablet_nav_bottom,
        TabletNavPlacement.LEFT to R.string.pref_tablet_nav_left,
    )

    SettingsScaffold(title = stringResource(R.string.pref_tablet_interface), onBackClick = onBackClick) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 180.dp),
        ) {
            Column(Modifier.selectableGroup()) {
                SettingsGroup(
                    title = stringResource(R.string.pref_tablet_nav_placement),
                    items = layouts.map { (option, label) ->
                        { shape ->
                            val selected = placement == option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 76.dp)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = hapticAction { prefs.setTabletNavPlacement(option) },
                                    )
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                                RadioButton(selected = selected, onClick = null)
                            }
                        }
                    },
                )
            }
            SettingsGroup(
                title = stringResource(R.string.settings_cat_appearance),
                items = listOf(
                    { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_tablet_side_player),
                            subtitle = stringResource(R.string.pref_tablet_side_player_sub),
                            hasSwitch = true,
                            switchState = sidePlayer,
                            onSwitchChange = prefs::setTabletSidePlayerEnabled,
                            highlightKey = "pref_tablet_side_player",
                        )
                    },
                    { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.pref_nav_hide_labels),
                            subtitle = stringResource(R.string.pref_nav_hide_labels_sub),
                            hasSwitch = true,
                            switchState = hideLabels,
                            onSwitchChange = prefs::setNavHideLabels,
                        )
                    },
                ),
            )
        }
    }
}
