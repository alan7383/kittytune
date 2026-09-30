package com.alananasss.kittytune.ui.profile

import java.text.Normalizer
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsHighlightManager
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.player.PlayerViewModel



/**
 * The settings screen, structured exactly like KittyTune Desktop:
 * - A top search bar with live filtering across all settings, keywords, and direct toggle switches.
 * - The 7 clean desktop categories: Interface, Audio, Sources, Storage, Sync, Network, Misc.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            // AOSP Settings Search Bar
            SettingsHomeSearchBar(
                onClick = { navController.navigate("settings_search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp)
            ) {
                SettingsCategory.entries.forEach { category ->
                    item(key = "cat-${category.name}") {
                        SettingsGroup(
                            title = stringResource(category.titleRes),
                            items = category.entriesFor().map { entry ->
                                { shape ->
                                    SettingsItem(
                                        shape = shape,
                                        title = stringResource(entry.titleRes),
                                        subtitle = entry.subtitleRes?.let { stringResource(it) },
                                        icon = entry.icon,
                                        onClick = { navController.navigate(entry.route) }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * AOSP Settings homepage search bar card (SearchBarStyle_v2 in com.android.settings_17.apk).
 * Tapping it navigates to the dedicated AOSP settings search screen.
 */
@Composable
fun SettingsHomeSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_homepage_search),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.homepage_search),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The Interface category's sub-pages, rendered identically to Desktop Screenshot 5:
 * Thèmes, Design du lecteur, Barre de navigation, Paroles.
 */
@Composable
fun InterfaceSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_cat_interface),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp)
        ) {
            item {
                SettingsGroup(
                    items = SettingsSubPage.interfacePages.map { page ->
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(page.titleRes),
                                subtitle = page.subtitleRes?.let { stringResource(it) },
                                icon = page.icon,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { navController.navigate(page.route) }
                            )
                        }
                    }
                )
            }
        }
    }
}
