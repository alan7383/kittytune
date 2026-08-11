package com.alananasss.kittytune.ui.musicimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicImportCoordinator
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import androidx.compose.material.icons.rounded.AccountCircle

@Composable
fun MusicImportScreen(
    onBackClick: () -> Unit,
    onPlatformSelected: (String) -> Unit,
    onLoginClick: () -> Unit = {},
    viewModel: MusicImportViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val tokenManager = remember { com.alananasss.kittytune.data.TokenManager(context) }
    val isLoggedIn = remember(tokenManager) { tokenManager.hasAccessToken() && !tokenManager.isGuestMode() }

    var platformPendingAuth by remember { mutableStateOf<MusicApi?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshConnection()
    }

    val pendingAuth by MusicImportCoordinator.pendingAuth.collectAsState()

    LaunchedEffect(pendingAuth) {
        val auth = pendingAuth
        if (auth != null) {
            val provider = auth.integration?.type
            viewModel.markConnecting(false)
            viewModel.checkPendingAuth()
            if (provider != null) {
                onPlatformSelected(provider)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (viewModel.isConnecting) {
                    coroutineScope.launch {
                        delay(500)
                        viewModel.markConnecting(false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.music_import_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!isLoggedIn) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.music_import_login_required),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onLoginClick,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_menu_login),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 180.dp)
                ) {
                item {
                    Text(
                        text = stringResource(R.string.music_import_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                item {
                    SettingsGroup(
                        title = stringResource(R.string.music_import_platforms_header),
                        items = viewModel.platforms.map { platform ->
                            { shape ->
                                val visual = platform.visual()
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(platform.labelRes()),
                                    subtitle = if (viewModel.isConnected(platform)) {
                                        stringResource(R.string.music_import_connected)
                                    } else {
                                        stringResource(R.string.music_import_connect)
                                    },
                                    icon = visual.icon,
                                    trailingText = if (viewModel.isConnected(platform)) {
                                        stringResource(R.string.music_import_manage)
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        if (viewModel.isConnected(platform)) {
                                            onPlatformSelected(platform.providerName)
                                        } else {
                                            platformPendingAuth = platform
                                        }
                                    }
                                )
                            }
                        }
                    )
                }



                item {
                    Text(
                        text = stringResource(R.string.music_import_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }

            if (viewModel.isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp)
                )
            }
        }
    }
    }

    if (platformPendingAuth != null) {
        AlertDialog(
            onDismissRequest = { platformPendingAuth = null },
            title = { Text(stringResource(R.string.music_import_auth_warning_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_auth_open_with_hint),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    )
                    Text(
                        text = stringResource(R.string.music_import_auth_warning_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        val platform = platformPendingAuth!!
                        platformPendingAuth = null
                        viewModel.markConnecting(true)
                        viewModel.markAuthError(null)
                        MusicApiAuthLauncher.launch(context, platform)
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.music_import_auth_warning_ok))
                }
            }
        )
    }
}

fun MusicApi.labelRes(): Int = when (this) {
    MusicApi.SPOTIFY -> R.string.music_provider_spotify
    MusicApi.APPLE_MUSIC -> R.string.music_provider_apple_music
    MusicApi.YOUTUBE_MUSIC -> R.string.music_provider_youtube_music
    MusicApi.DEEZER -> R.string.music_provider_deezer
    MusicApi.TIDAL -> R.string.music_provider_tidal
    MusicApi.AMAZON_MUSIC -> R.string.music_provider_amazon_music
    MusicApi.BOOMPLAY -> R.string.music_provider_boomplay
}
