package com.alananasss.kittytune.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.text.font.FontWeight
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alananasss.kittytune.ui.common.SafeWebView
import androidx.activity.compose.BackHandler
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.alananasss.kittytune.data.AchievementManager
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.ui.profile.WrappedHubScreen
import com.alananasss.kittytune.ui.common.AchievementNotification
import com.alananasss.kittytune.ui.common.AchievementNotificationManager
import com.alananasss.kittytune.ui.common.AchievementPopup
import com.alananasss.kittytune.ui.common.UltimateCompletionOverlay
import com.alananasss.kittytune.ui.home.*
import com.alananasss.kittytune.ui.library.LibraryScreen
import com.alananasss.kittytune.ui.library.PlaylistDetailScreen
import com.alananasss.kittytune.ui.library.PlaylistFansScreen
import com.alananasss.kittytune.ui.login.LoginScreen
import com.alananasss.kittytune.ui.login.WelcomeScreen
import com.alananasss.kittytune.data.UpdateManager
import com.alananasss.kittytune.data.UpdateStatus
import com.alananasss.kittytune.ui.common.UpdateScreen
import com.alananasss.kittytune.R

import com.alananasss.kittytune.ui.navigation.Screen
import com.alananasss.kittytune.ui.navigation.clippedComposable
import com.alananasss.kittytune.ui.navigation.AyuBackGestureBridge
import com.alananasss.kittytune.ui.navigation.AyuBackState
import com.alananasss.kittytune.ui.navigation.LocalAyuBackState
import com.alananasss.kittytune.ui.player.*
import com.alananasss.kittytune.ui.profile.integrations.*
import com.alananasss.kittytune.ui.player.lyrics.LyricsScreen
import com.alananasss.kittytune.ui.profile.*
import com.alananasss.kittytune.ui.profile.integrations.*
import com.alananasss.kittytune.ui.musicimport.*
import com.alananasss.kittytune.ui.recognition.RecognitionScreen
import com.alananasss.kittytune.ui.upload.UploadScreen
import com.alananasss.kittytune.ui.track.TrackDetailScreen
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.navigation.KittyUnifiedBottomBar
import com.alananasss.kittytune.ui.navigation.KittyNavigationRail
import com.alananasss.kittytune.ui.navigation.KittyTab
import com.alananasss.kittytune.ui.modifiers.progressiveBlur
import com.alananasss.kittytune.ui.modifiers.BlurDirection
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.clipToBounds
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * How long `AnimatedContent` must keep the popped destination composed. AyuGram's commit animation
 * runs for 375ms ([com.alananasss.kittytune.ui.navigation.AyuBack.POST_COMMIT_DURATION_MS]).
 * 450ms = 375ms + ~1 frame startup delay + rendering jitter headroom on slow devices.
 * The closing screen must stay composed for the *entire* animation; if `AnimatedContent` disposes
 * it early, the dismissed screen flashes back on top of the revealed one for the remaining frames.
 */
private const val AYU_BACK_KEEP_ALIVE_MS = 450

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    shouldOpenSearch: Boolean = false,
    initialSearchQuery: String? = null,
    onSearchHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val prefs = remember { PlayerPreferences(context) }

    val tokenManager = remember { TokenManager(context) }

    val bottomMenuStyle by prefs.bottomMenuStyleFlow().collectAsState(initial = prefs.getBottomMenuStyle())
    val rawBottomMenuBlurEnabled by prefs.bottomMenuBlurFlow().collectAsState(initial = prefs.getBottomMenuBlurEnabled())

    var startDestination by remember {
        mutableStateOf(
            when {
                prefs.isSetupCompleted() && (!tokenManager.getAccessToken().isNullOrEmpty() || tokenManager.isGuestMode()) -> {
                    val destPref = prefs.getStartDestination()
                    if (destPref == StartDestination.LIBRARY) Screen.Library.route else Screen.Home.route
                }
                else -> Screen.Welcome.route
            }
        )
    }
    var showProfileMenu by remember { mutableStateOf(false) }
    val allAchievementsUnlocked by AchievementManager.isAllUnlocked.collectAsState()
    var showCompletionScreen by remember { mutableStateOf(false) }
    var showPopups by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }

    var wasInQueue by remember { mutableStateOf(false) }

    LaunchedEffect(currentDestination?.route) {
        val route = currentDestination?.route
        if (route == "expanded_queue") {
            wasInQueue = true
        } else if (wasInQueue) {
            wasInQueue = false
            playerViewModel.isPlayerExpanded = true
        }
    }

    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "achievement_popups_enabled") {
                showPopups = prefs.getAchievementPopupsEnabled()
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var currentNotification by remember { mutableStateOf<AchievementNotification?>(null) }
    var animatingNotification by remember { mutableStateOf<AchievementNotification?>(null) }

    fun navigateToAuthenticatedStart() {
        val targetRoute = if (prefs.getStartDestination() == StartDestination.LIBRARY) {
            Screen.Library.route
        } else {
            Screen.Home.route
        }

        startDestination = targetRoute
        playerViewModel.fetchUserProfile()
        homeViewModel.loadData()
        navController.navigate(targetRoute) {
            popUpTo(Screen.Welcome.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    if (currentNotification != null) {
        animatingNotification = currentNotification
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                SessionManager.requestSessionRefresh(
                    context = context,
                    force = tokenManager.shouldRefreshAccessToken()
                )
                showPopups = prefs.getAchievementPopupsEnabled()
                AchievementManager.checkDailyStreak()
                playerViewModel.syncWithCurrentPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        AchievementNotificationManager.notifications.collect { notification ->
            if (showPopups || notification.iconEmoji == "🌙") {
                currentNotification = notification
                delay(5000)
                currentNotification = null
            }
        }
    }

    LaunchedEffect(Unit) {
        SessionManager.sessionReadyEvent.collect {
            val tm = TokenManager(context)
            val route = navController.currentBackStackEntry?.destination?.route
            val recoveredLogin = !tm.isGuestMode() && !tm.getAccessToken().isNullOrEmpty()

            if (recoveredLogin && route == Screen.Welcome.route && prefs.isSetupCompleted()) {
                navigateToAuthenticatedStart()
            }
        }
    }

    LaunchedEffect(shouldOpenSearch, currentDestination) {
        if (shouldOpenSearch && currentDestination != null) {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
            homeViewModel.activateSearch()
            if (!initialSearchQuery.isNullOrBlank()) {
                homeViewModel.onSearchQueryChanged(initialSearchQuery)
            }
            onSearchHandled()
        }
    }

    LaunchedEffect(allAchievementsUnlocked) {
        if (allAchievementsUnlocked) {
            showCompletionScreen = true
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        val hasToken = !tokenManager.getAccessToken().isNullOrEmpty()

        if (hasToken) {
            SessionManager.requestSessionRefresh(
                context = context,
                force = tokenManager.shouldRefreshAccessToken()
            )
            playerViewModel.fetchUserProfile()
            if (startDestination == Screen.Welcome.route && prefs.isSetupCompleted()) {
                navigateToAuthenticatedStart()
            }
        }
    }

    if (playerViewModel.showLyricsSheet) {
        DisposableEffect(Unit) {
            val window = context.findActivity()?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    LaunchedEffect(playerViewModel.navigateToPlaylistId) {
        playerViewModel.navigateToPlaylistId?.let { destinationId ->
            playerViewModel.isPlayerExpanded = false
            when {
                destinationId == "expanded_queue" -> navController.navigate("expanded_queue")
                destinationId == "your_mix" -> navController.navigate(Screen.Home.route)
                destinationId.startsWith("spotify_artist:") -> navController.navigate("spotify_artist/${destinationId.removePrefix("spotify_artist:")}")
                destinationId.startsWith("spotify_radio:") || destinationId.startsWith("station_spotify:") -> navController.navigate("playlist_detail/$destinationId")
                destinationId.startsWith("profile:") -> {
                    val target = destinationId.removePrefix("profile:")
                    if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                        val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                        navController.navigate("spotify_artist/$clean")
                    } else if (target.startsWith("deezer:") || target.startsWith("tidal:") || target.startsWith("qobuz:")) {
                        navController.navigate("playlist_detail/$target")
                    } else {
                        navController.navigate("profile/$target")
                    }
                }
                destinationId.startsWith("tag:") -> navController.navigate("tag/${destinationId.removePrefix("tag:")}")
                destinationId.startsWith("track_detail:") -> navController.navigate("track_detail/${destinationId.removePrefix("track_detail:")}")
                destinationId.startsWith("edit_track:") -> navController.navigate(Screen.Upload.route)
                else -> navController.navigate("playlist_detail/$destinationId")
            }
            playerViewModel.onNavigationHandled()
        }
    }

    BackHandler(enabled = playerViewModel.showLyricsSheet) {
        playerViewModel.showLyricsSheet = false
        playerViewModel.isSearchingLyrics = false
    }

    BackHandler(enabled = playerViewModel.isSidePlayerOpen && !playerViewModel.isPlayerExpanded) {
        playerViewModel.isSidePlayerOpen = false
    }

    val updateStatus by UpdateManager.status.collectAsState()
    val downloadProgress by UpdateManager.downloadProgress.collectAsState()
    val totalDownloadSize by UpdateManager.downloadSize.collectAsState()
    val isUpdateDownloaded by UpdateManager.isDownloaded.collectAsState()
    val releaseInfo = UpdateManager.releaseInfo

    LaunchedEffect(updateStatus) {
        if (updateStatus == UpdateStatus.READY_TO_INSTALL) {
            UpdateManager.installUpdate(context.applicationContext)
            UpdateManager.dismiss()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val backDensity = LocalDensity.current
    val ayuBackState = remember(backDensity.density) { AyuBackState(backDensity.density) }
    LaunchedEffect(Unit) {
        playerViewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowSizeInfo = rememberWindowSizeInfo(maxWidth, maxHeight)
        val actualBottomMenuBlurEnabled = (bottomMenuStyle == "modern" || windowSizeInfo.showTabletDock) && rawBottomMenuBlurEnabled
        val showBottomUi = !playerViewModel.isPlayerExpanded

        val currentRoute = currentDestination?.route ?: startDestination
        val isFullScreenRoute = currentRoute == Screen.Login.route ||
                currentRoute.startsWith("login") ||
                currentRoute == Screen.Welcome.route ||
                currentRoute == "update" ||
                currentRoute.startsWith("chat/") ||
                currentRoute.startsWith("music_import/") ||
                currentRoute == "music_import_transfer" ||
                currentRoute == Screen.Recognition.route ||
                currentRoute == "proxy_settings" ||
                currentRoute == "zapret_settings" ||
                currentRoute == "discord_login" ||
                currentRoute == "vk_login" ||
                currentRoute == "deezer_login" ||
                currentRoute == "tidal_login" ||
                currentRoute.startsWith("yearly_playback") ||
                currentRoute.startsWith("listening_stats") ||
                currentRoute == "storage" ||
                currentRoute.startsWith("storage_downloads")

        val hideNavRail = currentRoute == Screen.Login.route ||
                currentRoute.startsWith("login") ||
                currentRoute == Screen.Welcome.route ||
                currentRoute == "update" ||
                currentRoute == "discord_login" ||
                currentRoute == "vk_login" ||
                currentRoute == "deezer_login" ||
                currentRoute == "tidal_login" ||
                currentRoute.startsWith("yearly_playback") ||
                currentRoute.startsWith("listening_stats") ||
                currentRoute == "storage" ||
                currentRoute.startsWith("storage_downloads")

        val isMiniPlayerVisible = playerViewModel.currentTrack != null && !playerViewModel.isPlayerExpanded && !isFullScreenRoute

        val snackbarPadding by animateDpAsState(
            targetValue = if (isMiniPlayerVisible) 90.dp else 16.dp,
            label = "snackbarPadding"
        )

        Scaffold(
            snackbarHost = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = snackbarPadding),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .widthIn(max = 400.dp)
                        )
                    }
                }
            },
            bottomBar = {
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            val allTabKeys = listOf("home", "search", "genres", "library")
            val bottomNavItemsKeys by prefs.bottomMenuItemsFlow().collectAsState(initial = prefs.getBottomMenuItems())
            val bottomNavOrder by prefs.bottomMenuOrderFlow().collectAsState(initial = prefs.getBottomMenuOrder())

            val orderedTabKeys = remember(bottomNavOrder) {
                (bottomNavOrder.filter { it in allTabKeys } +
                    allTabKeys.filter { it !in bottomNavOrder }).distinct()
            }

            val tabs = orderedTabKeys.mapNotNull { key ->
                val screen = when (key) {
                    "home" -> Screen.Home
                    "search" -> Screen.Search
                    "genres" -> Screen.Explore
                    "library" -> Screen.Library
                    else -> null
                } ?: return@mapNotNull null

                KittyTab(
                    title = stringResource(screen.titleResId),
                    icon = screen.icon ?: Icons.Rounded.Home,
                    route = screen.route,
                    visible = bottomNavItemsKeys.contains(key)
                )
            }

            val mainTabRoutes = remember(orderedTabKeys) {
                orderedTabKeys.mapNotNull { key ->
                    when (key) {
                        "home" -> Screen.Home.route
                        "genres" -> Screen.Explore.route
                        "library" -> Screen.Library.route
                        else -> null
                    }
                }.distinct()
            }
            LaunchedEffect(mainTabRoutes) {
                ayuBackState.tabRoutes = mainTabRoutes
            }

            val selectedRoute = tabs.find { tab ->
                when (tab.route) {
                    Screen.Home.route -> {
                        currentDestination?.hierarchy?.any { it.route == tab.route } == true && !homeViewModel.isSearching
                    }
                    Screen.Search.route -> {
                        currentDestination?.hierarchy?.any { it.route == Screen.Home.route } == true && homeViewModel.isSearching
                    }
                    else -> {
                        currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    }
                }
            }?.route

            val fabSetting by prefs.bottomMenuFabFlow().collectAsState(initial = prefs.getBottomMenuFab())
            val onFabClick: () -> Unit = {
                val currentRoute = currentDestination?.route
                when {
                    fabSetting == "settings" -> {
                        if (currentRoute != "settings") navController.navigate("settings")
                    }
                    fabSetting == "recognition" -> {
                        if (currentRoute != "recognition") navController.navigate("recognition")
                    }
                    fabSetting == "achievements" -> {
                        if (currentRoute != "achievements") navController.navigate("achievements")
                    }
                    fabSetting == "stats" -> {
                        if (currentRoute != "listening_stats") navController.navigate("listening_stats")
                    }
                    fabSetting == "liked" -> {
                        val currentPlaylistId = navBackStackEntry?.arguments?.getString("playlistId")
                        if (currentRoute != "playlist_detail/{playlistId}" || currentPlaylistId != "likes") {
                            navController.navigate("playlist_detail/likes")
                        }
                    }
                    fabSetting == "downloads" -> {
                        val currentPlaylistId = navBackStackEntry?.arguments?.getString("playlistId")
                        if (currentRoute != "playlist_detail/{playlistId}" || currentPlaylistId != "downloads") {
                            navController.navigate("playlist_detail/downloads")
                        }
                    }
                    fabSetting == "local" -> {
                        val currentPlaylistId = navBackStackEntry?.arguments?.getString("playlistId")
                        if (currentRoute != "playlist_detail/{playlistId}" || currentPlaylistId != "local_files") {
                            navController.navigate("playlist_detail/local_files")
                        }
                    }
                    fabSetting.startsWith("playlist:") -> {
                        val id = fabSetting.removePrefix("playlist:")
                        val currentPlaylistId = navBackStackEntry?.arguments?.getString("playlistId")
                        if (currentRoute != "playlist_detail/{playlistId}" || currentPlaylistId != id) {
                            navController.navigate("playlist_detail/$id")
                        }
                    }
                    else -> showProfileMenu = true
                }
            }
            val fabIcon = when {
                fabSetting == "settings" -> Icons.Rounded.Settings
                fabSetting == "recognition" -> Icons.Rounded.GraphicEq
                fabSetting == "achievements" -> Icons.Rounded.EmojiEvents
                fabSetting == "stats" -> Icons.Rounded.BarChart
                fabSetting == "liked" -> Icons.Rounded.Favorite
                fabSetting == "downloads" -> Icons.Rounded.Download
                fabSetting == "local" -> Icons.Rounded.SdStorage
                fabSetting.startsWith("playlist:") -> Icons.AutoMirrored.Rounded.QueueMusic
                else -> Icons.Rounded.Person
            }

            val fabLabel = when {
                fabSetting == "settings" -> stringResource(R.string.pref_bottom_menu_fab_settings)
                fabSetting == "recognition" -> stringResource(R.string.pref_bottom_menu_fab_recognition)
                fabSetting == "achievements" -> stringResource(R.string.achievements_title)
                fabSetting == "stats" -> stringResource(R.string.pref_bottom_menu_fab_stats)
                fabSetting == "liked" -> stringResource(R.string.lib_liked_tracks)
                fabSetting == "downloads" -> stringResource(R.string.lib_downloads)
                fabSetting == "local" -> stringResource(R.string.lib_local_media)
                fabSetting.startsWith("playlist:") -> stringResource(R.string.pref_bottom_menu_fab_playlist)
                else -> stringResource(R.string.pref_bottom_menu_fab_profile)
            }

            val onTabSelected: (KittyTab) -> Unit = { tab ->
                if (tab.route == Screen.Search.route) {
                    val isAlreadyOnHome = currentDestination?.hierarchy?.any { it.route == Screen.Home.route } == true
                    if (!isAlreadyOnHome) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                    homeViewModel.activateSearch()
                } else {
                    val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    if (!isSelected) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    } else if (tab.route == Screen.Home.route && homeViewModel.isSearching) {
                        homeViewModel.clearSearch()
                    }
                }
            }
            val isSidePlayerVisible = windowSizeInfo.showTabletDock && playerViewModel.isSidePlayerOpen && !playerViewModel.isPlayerExpanded && playerViewModel.currentTrack != null
            val sidePanelWidth = 420.dp
            val animatedSidePanelWidth by animateDpAsState(
                targetValue = if (isSidePlayerVisible) sidePanelWidth else 0.dp,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 380f
                ),
                label = "sidePanelWidth"
            )

            Row(modifier = Modifier.fillMaxSize()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = windowSizeInfo.showNavRail && showBottomUi && !hideNavRail,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + expandHorizontally(expandFrom = Alignment.Start),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + shrinkHorizontally(shrinkTowards = Alignment.Start)
                ) {
                    KittyNavigationRail(
                        tabs = tabs,
                        selectedRoute = selectedRoute,
                        onTabSelected = onTabSelected,
                        onFabClick = onFabClick,
                        fabIcon = fabIcon,
                        fabLabel = fabLabel,
                        playerViewModel = playerViewModel,
                        onPlayerClick = { playerViewModel.isPlayerExpanded = true }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (windowSizeInfo.showNavRail && showBottomUi && !hideNavRail) {
                                Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val density = LocalDensity.current
                    val statusBarHeightPx = with(density) {
                        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
                    }
                    val bottomBarHeightPx = with(density) { if (windowSizeInfo.showTabletDock) 130.dp.toPx() else 150.dp.toPx() }

                    AyuBackGestureBridge(
                        navController,
                        ayuBackState,
                        gesturesEnabled = !playerViewModel.isPlayerExpanded && !playerViewModel.showLyricsSheet
                    )

                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalAyuBackState provides ayuBackState
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (!isFullScreenRoute) {
                                        Modifier.progressiveBlur(
                                            blurRadius = 40f,
                                            height = statusBarHeightPx * 1.15f,
                                            direction = BlurDirection.TOP
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (actualBottomMenuBlurEnabled && !isFullScreenRoute && (windowSizeInfo.showPhoneBottomBar || windowSizeInfo.showTabletDock)) {
                                        Modifier.progressiveBlur(
                                            blurRadius = 40f,
                                            height = bottomBarHeightPx,
                                            direction = BlurDirection.BOTTOM
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                        enterTransition = {
                            fadeIn(
                                animationSpec = tween(AYU_BACK_KEEP_ALIVE_MS),
                                initialAlpha = 1f
                            )
                        },
                        exitTransition = {
                            fadeOut(
                                animationSpec = tween(AYU_BACK_KEEP_ALIVE_MS),
                                targetAlpha = 1f
                            )
                        },
                        popEnterTransition = {
                            fadeIn(
                                animationSpec = tween(AYU_BACK_KEEP_ALIVE_MS),
                                initialAlpha = 1f
                            )
                        },
                        popExitTransition = {
                            fadeOut(
                                animationSpec = tween(AYU_BACK_KEEP_ALIVE_MS),
                                targetAlpha = 1f
                            )
                        },
                        predictivePopEnterTransition = {
                            fadeIn(
                                animationSpec = tween(AYU_BACK_KEEP_ALIVE_MS),
                                initialAlpha = 1f
                            )
                        },
                        predictivePopExitTransition = {
                            fadeOut(
                                animationSpec = tween(AYU_BACK_KEEP_ALIVE_MS),
                                targetAlpha = 1f
                            )
                        }
                    ) {
                        clippedComposable(Screen.Welcome.route) { backStackEntry ->
                            val justLoggedIn by backStackEntry.savedStateHandle
                                .getStateFlow("login_success", false)
                                .collectAsState()

                            WelcomeScreen(
                                onLoginClick = { navController.navigate("login?fromSetup=true") },
                                justLoggedIn = justLoggedIn,
                                onClearJustLoggedIn = {
                                    backStackEntry.savedStateHandle["login_success"] = false
                                },
                                onGuestClick = {
                                    val tm = TokenManager(context)
                                    tm.setGuestMode(true)
                                    prefs.setSetupCompleted(true)
                                    homeViewModel.loadData()
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Welcome.route) { inclusive = true }
                                    }
                                },
                                onSetupComplete = {
                                    prefs.setSetupCompleted(true)
                                    val tm = TokenManager(context)
                                    if (tm.getAccessToken().isNullOrEmpty()) {
                                        tm.setGuestMode(true)
                                    } else {
                                        tm.setGuestMode(false)
                                    }
                                    homeViewModel.loadData()
                                    playerViewModel.fetchUserProfile()
                                    val targetRoute = if (prefs.getStartDestination() == StartDestination.LIBRARY) {
                                        Screen.Library.route
                                    } else {
                                        Screen.Home.route
                                    }
                                    navController.navigate(targetRoute) {
                                        popUpTo(Screen.Welcome.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        clippedComposable(Screen.Home.route) {
                            HomeScreen(playerViewModel, homeViewModel, onNavigate = { id ->
                                when {
                                    id == "login_required" || id == "my_profile_menu" -> {
                                        showProfileMenu = true
                                    }
                                    id == "yearly_playback" -> navController.navigate("wrapped_hub")
                                    id == "listening_stats" -> navController.navigate("listening_stats")
                                    id == "charts" -> navController.navigate("charts")
                                    id == "genres" -> navController.navigate("genres")
                                    id == "new_releases" -> navController.navigate("new_releases")
                                    id == "history" || id == Screen.History.route -> navController.navigate(Screen.History.route)
                                    id == "recognition" -> navController.navigate("recognition")
                                    id == "recognition_history" -> navController.navigate("recognition_history")
                                    id == "likes" -> navController.navigate("playlist_detail/likes")
                                    id == "downloads" -> navController.navigate("playlist_detail/downloads")
                                    id.startsWith("spotify_artist:") -> {
                                        navController.navigate("spotify_artist/${id.removePrefix("spotify_artist:")}")
                                    }
                                    id.startsWith("spotify_radio:") || id.startsWith("station_spotify:") -> {
                                        navController.navigate("playlist_detail/$id")
                                    }
                                    id.startsWith("deezer:") || id.startsWith("tidal:") || id.startsWith("qobuz:") -> {
                                        navController.navigate("playlist_detail/$id")
                                    }
                                    id.startsWith("profile:") -> {
                                        val target = id.removePrefix("profile:")
                                        if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                            val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                            navController.navigate("spotify_artist/$clean")
                                        } else if (target.startsWith("deezer:") || target.startsWith("tidal:") || target.startsWith("qobuz:")) {
                                            navController.navigate("playlist_detail/$target")
                                        } else {
                                            navController.navigate("profile/$target")
                                        }
                                    }
                                    id.startsWith("profile/") || id.startsWith("playlist_detail/") || id.startsWith("genre_playlists/") || id.startsWith("tag/") -> {
                                        navController.navigate(id)
                                    }
                                    id.startsWith("yt_radio:") -> {
                                        val rawUrl = id.removePrefix("yt_radio:")
                                        val encodedUrl = android.net.Uri.encode(rawUrl)
                                        navController.navigate("playlist_detail/yt_radio:$encodedUrl")
                                    }
                                    id.startsWith("station:") || id.startsWith("station_artist:") -> {
                                        navController.navigate("playlist_detail/$id")
                                    }
                                    else -> {
                                        navController.navigate("playlist_detail/$id")
                                    }
                                }
                            })
                        }

                        clippedComposable(Screen.Library.route) {
                            LibraryScreen(
                                onLoginClick = { navController.navigate(Screen.Login.route) },
                                onProfileClick = { showProfileMenu = true },
                                onHistoryClick = { navController.navigate(Screen.History.route) },
                                onImportClick = { navController.navigate("music_import") },
                                onUploadClick = { navController.navigate("upload") },
                                onPlaylistClick = { id ->
                                    when {
                                        id.startsWith("spotify_artist:") -> {
                                            navController.navigate("spotify_artist/${id.removePrefix("spotify_artist:")}")
                                        }
                                        id.startsWith("spotify_radio:") || id.startsWith("station_spotify:") -> {
                                            navController.navigate("playlist_detail/$id")
                                        }
                                        id.startsWith("deezer:") || id.startsWith("tidal:") || id.startsWith("qobuz:") -> {
                                            navController.navigate("playlist_detail/$id")
                                        }
                                        id.startsWith("profile:") -> {
                                            val target = id.removePrefix("profile:")
                                            if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                                navController.navigate("spotify_artist/$clean")
                                            } else if (target.startsWith("deezer:") || target.startsWith("tidal:") || target.startsWith("qobuz:")) {
                                                navController.navigate("playlist_detail/$target")
                                            } else {
                                                navController.navigate("profile/$target")
                                            }
                                        }
                                        else -> navController.navigate("playlist_detail/$id")
                                    }
                                },
                                onLikedTracksClick = { navController.navigate("playlist_detail/likes") },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable(
                            route = "login?fromSetup={fromSetup}",
                            arguments = listOf(
                                navArgument("fromSetup") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val fromSetup = backStackEntry.arguments?.getBoolean("fromSetup") ?: false
                            LoginScreen(
                                onLoginSuccess = {
                                    SessionManager.requestSessionRefresh(context, force = true)
                                    playerViewModel.fetchUserProfile()
                                    homeViewModel.loadData()
                                    if (fromSetup) {
                                        runCatching {
                                            navController.getBackStackEntry(Screen.Welcome.route).savedStateHandle["login_success"] = true
                                        }.onFailure {
                                            navController.previousBackStackEntry?.savedStateHandle?.set("login_success", true)
                                        }
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("expanded_queue") {
                            ExpandedQueueScreen(
                                viewModel = playerViewModel,
                                onClose = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("genres") {
                            GenresScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { route -> navController.navigate(route) }
                            )
                        }

                        clippedComposable(
                            route = "genre_detail/{genreName}/{genreQuery}",
                            arguments = listOf(
                                navArgument("genreName") { type = NavType.StringType },
                                navArgument("genreQuery") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            GenreDetailScreen(
                                genreName = backStackEntry.arguments?.getString("genreName") ?: "",
                                genreQuery = backStackEntry.arguments?.getString("genreQuery") ?: "",
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { route -> navController.navigate(route) },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("charts") {
                            ChartsScreen(
                                onBackClick = { navController.popBackStack() },
                                onPlaylistClick = { playlistId ->
                                    navController.navigate("playlist_detail/$playlistId")
                                },
                                onNavigate = { route ->
                                    when {
                                        route.startsWith("spotify_artist:") -> {
                                            navController.navigate("spotify_artist/${route.removePrefix("spotify_artist:")}")
                                        }
                                        route.startsWith("spotify_radio:") || route.startsWith("station_spotify:") -> {
                                            navController.navigate("playlist_detail/$route")
                                        }
                                        route.startsWith("profile:") -> {
                                            val target = route.removePrefix("profile:")
                                            if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                                navController.navigate("spotify_artist/$clean")
                                            } else {
                                                navController.navigate("profile/$target")
                                            }
                                        }
                                        route.startsWith("station_artist:") -> {
                                            navController.navigate("playlist_detail/$route")
                                        }
                                        else -> {
                                            navController.navigate(route)
                                        }
                                    }
                                },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("new_releases") {
                            NewReleasesScreen(
                                onBackClick = { navController.popBackStack() },
                                onPlaylistClick = { playlistId ->
                                    navController.navigate("playlist_detail/$playlistId")
                                },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable(
                            route = "playlist_detail/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                        ) {
                            PlaylistDetailScreen(
                                playlistId = it.arguments?.getString("playlistId") ?: "",
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { id ->
                                    when {
                                        id.startsWith("tag:") -> {
                                            navController.navigate("tag/${id.removePrefix("tag:")}")
                                        }
                                        id.startsWith("spotify_artist:") -> {
                                            navController.navigate("spotify_artist/${id.removePrefix("spotify_artist:")}")
                                        }
                                        id.startsWith("spotify_radio:") || id.startsWith("station_spotify:") -> {
                                            navController.navigate("playlist_detail/$id")
                                        }
                                        id.startsWith("deezer:") || id.startsWith("tidal:") || id.startsWith("qobuz:") -> {
                                            navController.navigate("playlist_detail/$id")
                                        }
                                        id.startsWith("profile:") -> {
                                            val target = id.removePrefix("profile:")
                                            if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                                navController.navigate("spotify_artist/$clean")
                                            } else if (target.startsWith("deezer:") || target.startsWith("tidal:") || target.startsWith("qobuz:")) {
                                                navController.navigate("playlist_detail/$target")
                                            } else {
                                                navController.navigate("profile/$target")
                                            }
                                        }
                                        id.startsWith("playlist_fans/") || id.startsWith("playlist_detail/") || id.startsWith("profile/") || id == "downloaded_playlists" -> {
                                            navController.navigate(id)
                                        }
                                        else -> {
                                            navController.navigate("playlist_detail/$id")
                                        }
                                    }
                                },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable(route = "downloaded_playlists") {
                            com.alananasss.kittytune.ui.library.DownloadedPlaylistsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { id ->
                                    navController.navigate("playlist_detail/$id")
                                }
                            )
                        }

                        clippedComposable(
                            route = "genre_playlists/{genreTitle}/{query}",
                            arguments = listOf(
                                navArgument("genreTitle") { type = NavType.StringType },
                                navArgument("query") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            GenrePlaylistsScreen(
                                genreTitle = backStackEntry.arguments?.getString("genreTitle") ?: "",
                                query = backStackEntry.arguments?.getString("query") ?: "",
                                onBackClick = { navController.popBackStack() },
                                onPlaylistClick = { playlistId ->
                                    navController.navigate("playlist_detail/$playlistId")
                                }
                            )
                        }

                        clippedComposable(
                            route = "spotify_artist/{artistId}",
                            arguments = listOf(navArgument("artistId") { type = NavType.StringType })
                        ) {
                            ProfileScreen(
                                userId = "spotify_artist:${it.arguments?.getString("artistId") ?: ""}",
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel,
                                onNavigate = { id ->
                                    when {
                                        id.startsWith("profile_section/") -> navController.navigate(id)
                                        id.startsWith("spotify_artist:") -> navController.navigate("spotify_artist/${id.removePrefix("spotify_artist:")}")
                                        id.startsWith("spotify_radio:") || id.startsWith("station_spotify:") -> navController.navigate("playlist_detail/$id")
                                        id.startsWith("profile:") -> {
                                            val target = id.removePrefix("profile:")
                                            if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                                navController.navigate("spotify_artist/$clean")
                                            } else {
                                                navController.navigate("profile/$target")
                                            }
                                        }
                                        id.startsWith("playlist_detail/") || id.startsWith("profile/") -> navController.navigate(id)
                                        else -> navController.navigate("playlist_detail/$id")
                                    }
                                }
                            )
                        }

                        clippedComposable(
                            route = "profile/{userId}",
                            arguments = listOf(navArgument("userId") { type = NavType.StringType })
                        ) {
                            ProfileScreen(
                                it.arguments?.getString("userId") ?: "",
                                { navController.popBackStack() },
                                playerViewModel,
                                onNavigate = { id ->
                                    when {
                                        id.startsWith("profile_section/") -> navController.navigate(id)
                                        id == Screen.Upload.route || id == "upload" -> navController.navigate(Screen.Upload.route)
                                        id == "history" || id == Screen.History.route -> navController.navigate(Screen.History.route)
                                        id == "notifications" -> navController.navigate("notifications")
                                        id == "conversations" -> navController.navigate("conversations")
                                        id == "recognition_history" -> navController.navigate("recognition_history")
                                        id == "likes" -> navController.navigate("playlist_detail/likes")
                                        id == "downloads" -> navController.navigate("playlist_detail/downloads")
                                        id.startsWith("spotify_artist:") -> navController.navigate("spotify_artist/${id.removePrefix("spotify_artist:")}")
                                        id.startsWith("spotify_radio:") || id.startsWith("station_spotify:") -> navController.navigate("playlist_detail/$id")
                                        id.startsWith("profile:") -> {
                                            val target = id.removePrefix("profile:")
                                            if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                                navController.navigate("spotify_artist/$clean")
                                            } else {
                                                navController.navigate("profile/$target")
                                            }
                                        }
                                        id.startsWith("followers:") -> navController.navigate("followers/${id.removePrefix("followers:")}")
                                        id.startsWith("followings:") -> navController.navigate("followings/${id.removePrefix("followings:")}")
                                        id.startsWith("station:") || id.startsWith("station_artist:") -> navController.navigate("playlist_detail/$id")
                                        id.startsWith("tag:") -> navController.navigate("tag/${id.removePrefix("tag:")}")
                                        id.startsWith("playlist_detail/") || id.startsWith("profile/") -> navController.navigate(id)
                                        else -> navController.navigate("playlist_detail/$id")
                                    }
                                }
                            )
                        }

                        clippedComposable(
                            route = "profile_section/{userId}/{section}?filter={filter}",
                            arguments = listOf(
                                navArgument("userId") { type = NavType.StringType },
                                navArgument("section") { type = NavType.StringType },
                                navArgument("filter") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val rawUserId = backStackEntry.arguments?.getString("userId") ?: ""
                            val userId = runCatching {
                                java.net.URLDecoder.decode(rawUserId, "UTF-8")
                            }.getOrDefault(rawUserId)
                            val section = backStackEntry.arguments?.getString("section") ?: ""
                            val filter = backStackEntry.arguments?.getString("filter")

                            val parentEntry = remember(backStackEntry) {
                                runCatching { navController.previousBackStackEntry }.getOrNull()
                            }
                            val isProfileParent = parentEntry?.destination?.route?.let { route ->
                                route.startsWith("profile/") || route.startsWith("spotify_artist/")
                            } == true

                            val profileViewModel: ProfileViewModel = if (isProfileParent) {
                                viewModel(parentEntry!!)
                            } else {
                                viewModel()
                            }

                            ProfileSectionScreen(
                                userId = userId,
                                section = section,
                                initialFilter = filter,
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel,
                                onNavigate = { id ->
                                    when {
                                        id.startsWith("profile_section/") -> navController.navigate(id)
                                        id.startsWith("spotify_artist:") -> navController.navigate("spotify_artist/${id.removePrefix("spotify_artist:")}")
                                        id.startsWith("spotify_radio:") || id.startsWith("station_spotify:") -> navController.navigate("playlist_detail/$id")
                                        id.startsWith("profile:") -> {
                                            val target = id.removePrefix("profile:")
                                            if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                                navController.navigate("spotify_artist/$clean")
                                            } else {
                                                navController.navigate("profile/$target")
                                            }
                                        }
                                        id.startsWith("playlist_detail/") || id.startsWith("profile/") -> navController.navigate(id)
                                        else -> navController.navigate("playlist_detail/$id")
                                    }
                                },
                                profileViewModel = profileViewModel
                            )
                        }

                        clippedComposable(
                            route = "followers/{userId}",
                            arguments = listOf(navArgument("userId") { type = NavType.StringType })
                        ) {
                            val uidStr = it.arguments?.getString("userId") ?: ""
                            com.alananasss.kittytune.ui.profile.UserListScreen(
                                userId = uidStr.toLongOrNull() ?: 0L,
                                type = "followers",
                                onBack = { navController.popBackStack() },
                                onUserClick = { uid -> navController.navigate("profile/$uid") }
                            )
                        }

                        clippedComposable(
                            route = "followings/{userId}",
                            arguments = listOf(navArgument("userId") { type = NavType.StringType })
                        ) {
                            val uidStr = it.arguments?.getString("userId") ?: ""
                            com.alananasss.kittytune.ui.profile.UserListScreen(
                                userId = uidStr.toLongOrNull() ?: 0L,
                                type = "followings",
                                onBack = { navController.popBackStack() },
                                onUserClick = { uid -> navController.navigate("profile/$uid") }
                            )
                        }

                        clippedComposable(
                            route = "tag/{tagName}",
                            arguments = listOf(navArgument("tagName") { type = NavType.StringType })
                        ) {
                            TagScreen(
                                it.arguments?.getString("tagName") ?: "",
                                { navController.popBackStack() },
                                playerViewModel
                            )
                        }

                        clippedComposable(
                            route = "track_detail/{trackId}?tab={tabIndex}",
                            arguments = listOf(
                                navArgument("trackId") { type = NavType.LongType },
                                navArgument("tabIndex") { type = NavType.IntType; defaultValue = 0 }
                            )
                        ) {
                            TrackDetailScreen(
                                it.arguments?.getLong("trackId") ?: 0L,
                                it.arguments?.getInt("tabIndex") ?: 0,
                                { navController.popBackStack() },
                                onNavigate = { id ->
                                    if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                                    else navController.navigate("playlist_detail/$id")
                                },
                                playerViewModel
                            )
                        }

                        clippedComposable(
                            route = "playlist_fans/{playlistId}?tab={tabIndex}",
                            arguments = listOf(
                                navArgument("playlistId") { type = NavType.StringType },
                                navArgument("tabIndex") { type = NavType.IntType; defaultValue = 0 }
                            )
                        ) { entry ->
                            PlaylistFansScreen(
                                playlistId = entry.arguments?.getString("playlistId") ?: "",
                                initialTab = entry.arguments?.getInt("tabIndex") ?: 0,
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { id ->
                                    if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                                }
                            )
                        }

                        clippedComposable("notifications") {
                            NotificationsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { id ->
                                    if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                                    else navController.navigate(id)
                                }
                            )
                        }

                        clippedComposable("conversations") {
                            ConversationsScreen(
                                onBackClick = { navController.popBackStack() },
                                onConversationClick = { conversationId, otherUserId, username ->
                                    navController.navigate("chat/$conversationId/$otherUserId/$username")
                                }
                            )
                        }

                        clippedComposable(
                            route = "chat/{conversationId}/{otherUserId}/{username}",
                            arguments = listOf(
                                navArgument("conversationId") { type = NavType.StringType },
                                navArgument("otherUserId") { type = NavType.StringType },
                                navArgument("username") { type = NavType.StringType }
                            )
                        ) { entry ->
                            ChatScreen(
                                conversationId = entry.arguments?.getString("conversationId") ?: "",
                                otherUserId = entry.arguments?.getString("otherUserId") ?: "",
                                username = entry.arguments?.getString("username") ?: "",
                                onBackClick = { navController.popBackStack() },
                                onProfileClick = { userId ->
                                    navController.navigate("profile/$userId")
                                },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("achievements") {
                            AchievementsScreen { navController.popBackStack() }
                        }

                        clippedComposable("listening_stats") {
                            val tokenManager = remember { TokenManager(context) }
                            val isGuest = tokenManager.isGuestMode()
                            ListeningStatsScreen(
                                onBackClick = { navController.popBackStack() },
                                onTrackClick = { track ->
                                    if (track.source == "soundcloud") {
                                        val stubTrack = com.alananasss.kittytune.domain.Track(
                                            id = track.trackId,
                                            title = track.title,
                                            user = com.alananasss.kittytune.domain.User(0, track.artistName, null),
                                            artworkUrl = track.artworkUrl,
                                            durationMs = 0L
                                        )
                                        playerViewModel.playPlaylist(listOf(stubTrack), 0)
                                    }
                                },
                                onArtistClick = { artist ->
                                    if (artist.source == "spotify" && !artist.permalink.isNullOrBlank()) {
                                        playerViewModel.navigateToSpotifyArtist(
                                            artist.permalink.removePrefix("spotify:artist:")
                                        )
                                    } else {
                                        playerViewModel.resolveAndNavigateToArtist(artist.name, artist.artistId)
                                    }
                                },
                                onNavigateToYearlyPlayback = { navController.navigate("wrapped_hub") },
                                isGuest = isGuest
                            )
                        }

                        clippedComposable(
                            route = "yearly_playback?year={year}",
                            arguments = listOf(
                                navArgument("year") {
                                    type = NavType.IntType
                                    defaultValue = 2025
                                }
                            )
                        ) { backStackEntry ->
                            val requestedYear = backStackEntry.arguments?.getInt("year") ?: 2025
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                if (playerViewModel.isPlaying) {
                                    playerViewModel.pause()
                                }
                            }
                            com.alananasss.kittytune.ui.yearlyplayback.YearlyPlaybackScreen(
                                year = requestedYear,
                                onClose = { navController.popBackStack() },
                                onOpenStats = {
                                    navController.popBackStack()
                                    navController.navigate("listening_stats")
                                },
                                onNavigateToPlaylist = { playlistUrn ->
                                    val target = if (playlistUrn.startsWith("soundcloud:system-playlists:")) {
                                        "system_playlist:$playlistUrn"
                                    } else {
                                        playlistUrn.removePrefix("soundcloud:playlists:").removePrefix("spotify:playlist:")
                                    }
                                    navController.navigate("playlist_detail/$target")
                                }
                            )
                        }

                        clippedComposable("wrapped_hub") {
                            val tokenManager = remember { TokenManager(context) }
                            val isGuest = tokenManager.isGuestMode()
                            WrappedHubScreen(
                                user = homeViewModel.userProfile,
                                isGuest = isGuest,
                                onBackClick = { navController.popBackStack() },
                                onLaunchStory = { year ->
                                    navController.navigate("yearly_playback?year=$year")
                                },
                                onOpenPlaylist = { playlistUrn ->
                                    val target = if (playlistUrn.startsWith("soundcloud:system-playlists:")) {
                                        "system_playlist:$playlistUrn"
                                    } else {
                                        playlistUrn.removePrefix("soundcloud:playlists:").removePrefix("spotify:playlist:")
                                    }
                                    navController.navigate("playlist_detail/$target")
                                }
                            )
                        }

                        clippedComposable("yearly_playback") {
                            val tokenManager = remember { TokenManager(context) }
                            val isGuest = tokenManager.isGuestMode()
                            WrappedHubScreen(
                                user = homeViewModel.userProfile,
                                isGuest = isGuest,
                                onBackClick = { navController.popBackStack() },
                                onLaunchStory = { year ->
                                    navController.navigate("yearly_playback?year=$year")
                                },
                                onOpenPlaylist = { playlistUrn ->
                                    val target = if (playlistUrn.startsWith("soundcloud:system-playlists:")) {
                                        "system_playlist:$playlistUrn"
                                    } else {
                                        playlistUrn.removePrefix("soundcloud:playlists:").removePrefix("spotify:playlist:")
                                    }
                                    navController.navigate("playlist_detail/$target")
                                }
                            )
                        }

                        clippedComposable(Screen.Recognition.route) {
                            RecognitionScreen(
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel,
                                onNavigate = { dest -> navController.navigate(dest) }
                            )
                        }
                        clippedComposable(Screen.RecognitionHistory.route) {
                            com.alananasss.kittytune.ui.recognition.RecognitionHistoryScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { dest -> navController.navigate(dest) },
                                playerViewModel = playerViewModel
                            )
                        }
                        clippedComposable(Screen.History.route) {
                            com.alananasss.kittytune.ui.history.HistoryScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { dest ->
                                    when {
                                        dest == Screen.Home.route || dest == "home" -> navController.navigate(Screen.Home.route)
                                        dest.startsWith("spotify_artist:") -> {
                                            navController.navigate("spotify_artist/${dest.removePrefix("spotify_artist:")}")
                                        }
                                        dest.startsWith("spotify_radio:") || dest.startsWith("station_spotify:") -> {
                                            navController.navigate("playlist_detail/$dest")
                                        }
                                        dest.startsWith("profile:") -> {
                                            val target = dest.removePrefix("profile:")
                                            if (target.startsWith("spotify:artist:") || target.startsWith("spotify_artist:")) {
                                                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(target)
                                                navController.navigate("spotify_artist/$clean")
                                            } else {
                                                navController.navigate("profile/$target")
                                            }
                                        }
                                        dest.startsWith("playlist_detail/") || dest.startsWith("profile/") || dest.startsWith("tag/") || dest.startsWith("genre_playlists/") || dest.startsWith("track_detail/") -> {
                                            navController.navigate(dest)
                                        }
                                        dest.startsWith("tag:") -> {
                                            val tagName = dest.removePrefix("tag:")
                                            navController.navigate("tag/$tagName")
                                        }
                                        else -> {
                                            navController.navigate("playlist_detail/$dest")
                                        }
                                    }
                                },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("settings") {
                            SettingsScreen(navController, { navController.popBackStack() }, playerViewModel)
                        }

                        clippedComposable("settings_search") {
                            SettingsSearchScreen(
                                navController = navController,
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable(Screen.Upload.route) {
                            UploadScreen(
                                onBackClick = {
                                    playerViewModel.trackToEdit = null
                                    navController.popBackStack()
                                },
                                onLoginClick = { navController.navigate(Screen.Login.route) },
                                trackToEdit = playerViewModel.trackToEdit
                            )
                        }

                        clippedComposable("music_import") {
                            MusicImportScreen(
                                onBackClick = { navController.popBackStack() },
                                onPlatformSelected = { platform ->
                                    navController.navigate("music_import/$platform")
                                },
                                onLoginClick = { navController.navigate(Screen.Login.route) }
                            )
                        }

                        clippedComposable(
                            route = "music_import/{platform}",
                            arguments = listOf(navArgument("platform") { type = NavType.StringType })
                        ) { entry ->
                            val platform = entry.arguments?.getString("platform") ?: ""
                            MusicImportSelectionScreen(
                                platformProviderName = platform,
                                onBackClick = { navController.popBackStack() },
                                onStartTransfer = { navController.navigate("music_import_transfer") }
                            )
                        }

                        clippedComposable("music_import_transfer") {
                            MusicImportTransferScreen(
                                onBackClick = { navController.popBackStack() },
                                onDone = {
                                    navController.popBackStack(
                                        navController.graph.findStartDestination().id,
                                        inclusive = false
                                    )
                                }
                            )
                        }

                        clippedComposable("backup_restore") {
                            BackupRestoreScreen(onBackClick = { navController.popBackStack() })
                        }

                        clippedComposable("audio_settings") {
                            AudioSettingsScreen(
                                navController = navController,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("audio_playback_settings") {
                            AudioPlaybackSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("audio_quality_settings") {
                            AudioQualitySettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToDrmExplanation = { navController.navigate("drm_explanation") },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("audio_transitions_settings") {
                            AudioTransitionsSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("audio_sleep_settings") {
                            AudioSleepTimerSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("haptic_settings") {
                            com.alananasss.kittytune.ui.profile.HapticSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("content_filter_settings") {
                            com.alananasss.kittytune.ui.profile.BlockedContentSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("drm_explanation") {
                            DrmExplanationScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("lyrics_settings") {
                            LyricsSettingsScreen({ navController.popBackStack() }, playerViewModel)
                        }

                        clippedComposable("local_media_settings") {
                            LocalMediaSettingsScreen { navController.popBackStack() }
                        }

                        clippedComposable("appearance_settings") {
                            AppearanceSettingsScreen(
                                onNavigateToColors = { navController.navigate("color_palette") },
                                onNavigateToBottomBarSettings = { navController.navigate("bottom_bar_settings") },
                                onNavigateToAppIconSettings = { navController.navigate("app_icon_settings") },
                                onNavigateToPlayerCustomization = { navController.navigate("player_design_settings") },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("app_icon_settings") {
                            AppIconSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("bottom_bar_settings") {
                            BottomBarSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToFabSettings = { navController.navigate("fab_settings") },
                                playerViewModel = playerViewModel
                            )
                        }

                        clippedComposable("fab_settings") {
                            FabSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("color_palette") {
                            ColorPaletteScreen(onBackClick = { navController.popBackStack() })
                        }

                        clippedComposable("about") {
                            AboutScreen(
                                onBackClick = { navController.popBackStack() },
                                onLicensesClick = { navController.navigate("licenses") },
                                onCreditsClick = { navController.navigate("credits") }
                            )
                        }

                        clippedComposable("credits") {
                            CreditsScreen(onBackClick = { navController.popBackStack() })
                        }

                        clippedComposable("licenses") {
                            LicensesScreen(onBackClick = { navController.popBackStack() })
                        }

                        clippedComposable("storage") {
                            StorageScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToPlaylist = { route -> navController.navigate(route) },
                                onNavigateToDownloads = { tab -> navController.navigate("storage_downloads?tab=$tab") }
                            )
                        }

                        clippedComposable(
                            route = "storage_downloads?tab={tab}",
                            arguments = listOf(
                                navArgument("tab") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                }
                            )
                        ) { backStackEntry ->
                            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
                            StorageDownloadsScreen(
                                initialTab = tab,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToPlaylist = { route -> navController.navigate(route) }
                            )
                        }

                        clippedComposable("discord_settings") {
                            DiscordSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToLogin = { navController.navigate("discord_login") }
                            )
                        }

                        clippedComposable("discord_login") {
                            DiscordLoginScreen(
                                onBackClick = { navController.popBackStack() },
                                onLoginSuccess = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        clippedComposable("sync_settings") {
                            SyncSettingsScreen(
                                navController = navController,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("sync_devices_settings") {
                            SyncDevicesScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("sync_options_settings") {
                            SyncOptionsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("sync_advanced_settings") {
                            SyncAdvancedScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("proxy_settings") {
                            ProxySettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("zapret_settings") {
                            ZapretSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("interface_settings") {
                            InterfaceSettingsScreen(
                                navController = navController,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("sources_settings") {
                            SourcesSettingsScreen(
                                navController = navController,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("storage_settings") {
                            StorageSettingsScreen(
                                navController = navController,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("player_design_settings") {
                            PlayerCustomizationScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("misc_settings") {
                            MiscSettingsScreen(
                                navController = navController,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("misc_general_settings") {
                            MiscGeneralSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("accounts_settings") {
                            AccountsSettingsScreen(
                                currentUser = homeViewModel.userProfile,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToSoundCloud = { navController.navigate("soundcloud_account_settings") },
                                onNavigateToVk = { navController.navigate("vk_account_settings") },
                                onNavigateToDiscord = { navController.navigate("discord_settings") },
                                onNavigateToProviderOrder = { navController.navigate("provider_order_settings") },
                                onNavigateToQobuz = { navController.navigate("qobuz_settings") },
                                onNavigateToTidal = { navController.navigate("tidal_settings") },
                                onNavigateToDeezer = { navController.navigate("deezer_settings") }
                            )
                        }

                        clippedComposable("provider_order_settings") {
                            ProviderOrderScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("qobuz_settings") {
                            QobuzSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("tidal_settings") {
                            TidalSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToLogin = { navController.navigate("tidal_login") }
                            )
                        }

                        clippedComposable("deezer_settings") {
                            DeezerSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToLogin = { navController.navigate("deezer_login") }
                            )
                        }

                        clippedComposable("deezer_login") {
                            DeezerLoginScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("tidal_login") {
                            TidalLoginScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        clippedComposable("soundcloud_account_settings") {
                            SoundCloudAccountSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                                onNavigateToProfile = { userId ->
                                    navController.navigate("profile/$userId")
                                }
                            )
                        }

                        clippedComposable("vk_account_settings") {
                            VkAccountSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNavigateToWebViewLogin = { navController.navigate("vk_login") }
                            )
                        }

                        clippedComposable("vk_login") {
                            VkLoginScreen(
                                onBackClick = { navController.popBackStack() },
                                onLoginSuccess = { navController.popBackStack() }
                            )
                        }

                        }
                    }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .semantics { }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {})
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = (playerViewModel.showDismissUndoBar || playerViewModel.showAiSkipUndoBar) && showBottomUi && !isFullScreenRoute,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        if (playerViewModel.showAiSkipUndoBar) {
                            val trackTitle = playerViewModel.aiSkippedTrack?.title
                            val baseMsg = stringResource(R.string.ai_skip_undo_message)
                            val displayText = if (!trackTitle.isNullOrBlank()) "$baseMsg • $trackTitle" else baseMsg
                            com.alananasss.kittytune.ui.player.pixel.DismissUndoBar(
                                text = displayText,
                                onUndo = { playerViewModel.undoAiSkip() },
                                onClose = { playerViewModel.hideAiSkipUndoBar() }
                            )
                        } else {
                            com.alananasss.kittytune.ui.player.pixel.DismissUndoBar(
                                onUndo = { playerViewModel.undoDismissMiniPlayer() },
                                onClose = { playerViewModel.hideDismissUndoBar() }
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = windowSizeInfo.showPhoneBottomBar && showBottomUi && !isFullScreenRoute,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                    ) {
                        KittyUnifiedBottomBar(
                            tabs = tabs,
                            selectedRoute = selectedRoute,
                            onTabSelected = onTabSelected,
                            onFabClick = onFabClick,
                            fabIcon = fabIcon,
                            playerViewModel = playerViewModel,
                            onPlayerClick = { playerViewModel.isPlayerExpanded = true },
                            style = bottomMenuStyle,
                            blurEnabled = actualBottomMenuBlurEnabled
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = windowSizeInfo.showTabletDock && showBottomUi && !isFullScreenRoute,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                    ) {
                        com.alananasss.kittytune.ui.navigation.TabletBottomDock(
                            tabs = tabs,
                            selectedRoute = selectedRoute,
                            onTabSelected = onTabSelected,
                            onFabClick = onFabClick,
                            fabIcon = fabIcon,
                            fabLabel = fabLabel,
                            playerViewModel = playerViewModel,
                            onPlayerClick = {
                                if (windowSizeInfo.showTabletDock) {
                                    playerViewModel.isSidePlayerOpen = true
                                    playerViewModel.isPlayerExpanded = false
                                } else {
                                    playerViewModel.isPlayerExpanded = true
                                }
                            }
                        )
                    }
                }
            }

            if (animatedSidePanelWidth > 0.dp) {
                val panelAlpha = (animatedSidePanelWidth / sidePanelWidth).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(animatedSidePanelWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                        .alpha(panelAlpha)
                ) {
                    Box(
                        modifier = Modifier
                            .width(sidePanelWidth)
                            .fillMaxHeight()
                    ) {
                        com.alananasss.kittytune.ui.player.TabletSidePlayerPanel(
                            viewModel = playerViewModel,
                            onExpandFullScreen = {
                                playerViewModel.isPlayerExpanded = true
                                playerViewModel.isSidePlayerOpen = false
                            },
                            onClose = {
                                playerViewModel.isSidePlayerOpen = false
                            },
                            onOpenFullLyrics = {
                                playerViewModel.showLyricsSheet = true
                            },
                            onNavigateToArtist = { artistId: Long ->
                                navController.navigate("profile/$artistId")
                            }
                        )
                    }
                }
            }
        }
    }

        val navStackIds = navController.currentBackStack.collectAsState().value.map { it.id }
        BackHandler(enabled = ayuBackState.shouldVetoBack(navStackIds)) {
        }

        AnimatedVisibility(
            visible = playerViewModel.isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(350, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(350)),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerScreen(
                viewModel = playerViewModel,
                onClose = { playerViewModel.isPlayerExpanded = false }
            )
        }

        AnimatedVisibility(
            visible = playerViewModel.showLyricsSheet,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            LyricsScreen(
                viewModel = playerViewModel,
                onClose = {
                    playerViewModel.showLyricsSheet = false
                    playerViewModel.isSearchingLyrics = false
                }
            )
        }

        if (playerViewModel.showMenuSheet) {
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { playerViewModel.showMenuSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                MenuSheetContent(playerViewModel)
                Spacer(Modifier.height(32.dp))
            }
        }

        if (playerViewModel.showAiDetectionSheet) {
            com.alananasss.kittytune.ui.player.ai.AiDetectionBottomSheet(
                viewModel = playerViewModel,
                onDismissRequest = { playerViewModel.showAiDetectionSheet = false },
                onOpenSettings = {
                    playerViewModel.showAiDetectionSheet = false
                    navController.navigate("content_filter_settings")
                }
            )
        }

        playerViewModel.shareCardTrack?.let { cardTrack ->
            com.alananasss.kittytune.ui.share.ShareCardSheet(
                artwork = playerViewModel.shareCardArtwork,
                title = cardTrack.title ?: stringResource(R.string.untitled_track),
                artist = cardTrack.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                trackId = cardTrack.id,
                trackUrl = cardTrack.permalinkUrl,
                lyrics = playerViewModel.shareCardLyrics,
                onDismiss = { playerViewModel.dismissShareCard() },
            )
        }

        if (playerViewModel.showAddToPlaylistSheet) {
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { playerViewModel.showAddToPlaylistSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                AddToPlaylistContent(playerViewModel)
                Spacer(Modifier.height(32.dp))
            }
        }

        if (playerViewModel.showDetailsSheet) {
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { playerViewModel.showDetailsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ) {
                playerViewModel.selectedTrackForSheet?.let {
                    DetailsSheetContent(
                        it,
                        { playerViewModel.showDetailsSheet = false },
                        { playerViewModel.openComments() },
                        playerViewModel
                    )
                }
            }
        }

        if (playerViewModel.showCommentsSheet) {
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { playerViewModel.showCommentsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                CommentsSheetContent(playerViewModel) {
                    playerViewModel.showCommentsSheet = false
                }
            }
        }

        if (showProfileMenu) {
            val tokenManager = remember { TokenManager(context) }
            val isGuest = tokenManager.isGuestMode()
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { showProfileMenu = false },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ProfileMenuSheet(
                    user = homeViewModel.userProfile,
                    isGuest = isGuest,
                    onDismiss = { showProfileMenu = false },
                    onViewProfile = {
                        homeViewModel.userProfile?.id?.let {
                            navController.navigate("profile/$it")
                        }
                    },
                    onAchievementsClick = { navController.navigate("achievements") },
                    onListeningStatsClick = { navController.navigate("listening_stats") },
                    onYearlyPlaybackClick = { navController.navigate("wrapped_hub") },
                    onSettingsClick = { navController.navigate("settings") },
                    onAboutClick = { navController.navigate("about") },
                    onLogoutClick = {
                        if (isGuest) {
                            navController.navigate(Screen.Login.route)
                        } else {
                            tokenManager.logout()
                            navController.navigate(Screen.Welcome.route) {
                                popUpTo(0) { inclusive = true }
                            }
                            homeViewModel.loadData()
                        }
                    }
                )
                Spacer(Modifier.height(32.dp))
            }
        }



        if (showCompletionScreen) {
            UltimateCompletionOverlay(onDismiss = { showCompletionScreen = false })
        }

        if (updateStatus == UpdateStatus.AVAILABLE || updateStatus == UpdateStatus.DOWNLOADING) {
            Dialog(
                onDismissRequest = {
                    if (updateStatus != UpdateStatus.DOWNLOADING) {
                        UpdateManager.dismiss()
                    }
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                UpdateScreen(
                    release = releaseInfo,
                    status = updateStatus,
                    progress = downloadProgress,
                    totalSize = totalDownloadSize,
                    isDownloaded = isUpdateDownloaded,
                    onDownload = {
                        scope.launch { UpdateManager.downloadUpdate(context.applicationContext) }
                    },
                    onForceRedownload = {
                        scope.launch { UpdateManager.downloadUpdate(context.applicationContext, forceRedownload = true) }
                    },
                    onDismiss = {
                        UpdateManager.dismiss()
                    },
                    onBack = {
                        UpdateManager.dismiss()
                    }
                )
            }
        }

        if (playerViewModel.captchaUrl != null) {
            Dialog(
                onDismissRequest = { playerViewModel.onCaptchaSolved() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.security_check)) },
                            navigationIcon = {
                                IconButton(onClick = { playerViewModel.onCaptchaSolved() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                SafeWebView(ctx).apply {
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = Config.USER_AGENT
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(
                                            view: WebView?,
                                            url: String?
                                        ) {
                                            super.onPageFinished(view, url)
                                            CookieManager.getInstance().flush()
                                        }
                                    }
                                    loadUrl(playerViewModel.captchaUrl!!)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (playerViewModel.showSelectArtistDialog) {
            SelectArtistDialog(viewModel = playerViewModel)
        }

        AnimatedVisibility(
            visible = currentNotification != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(
                animationSpec = tween(300)
            ) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
            ) + fadeOut(
                animationSpec = tween(durationMillis = 200)
            ) + scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
                .zIndex(11f)
                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = Color.Black)
        ) {
            animatingNotification?.let {
                AchievementPopup(notification = it)
            }
        }
    }
}


