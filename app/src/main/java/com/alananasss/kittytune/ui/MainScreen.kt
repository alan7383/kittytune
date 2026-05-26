package com.alananasss.kittytune.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.View
import androidx.compose.ui.text.font.FontWeight
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.alananasss.kittytune.ui.navigation.ClippedScreen
import com.alananasss.kittytune.ui.navigation.getScreenCornerRadius
import com.alananasss.kittytune.ui.player.*
import com.alananasss.kittytune.ui.player.lyrics.LyricsScreen
import com.alananasss.kittytune.ui.profile.*
import com.alananasss.kittytune.ui.track.TrackDetailScreen
import com.alananasss.kittytune.ui.navigation.KittyUnifiedBottomBar
import com.alananasss.kittytune.ui.navigation.KittyTab
import com.alananasss.kittytune.ui.modifiers.progressiveBlur
import com.alananasss.kittytune.ui.modifiers.BlurDirection
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    shouldOpenSearch: Boolean = false,
    onSearchHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val bottomNavItems = listOf(Screen.Home, Screen.Library)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val prefs = remember { PlayerPreferences(context) }

    val tokenManager = remember { TokenManager(context) }

    val bottomMenuStyle by prefs.bottomMenuStyleFlow().collectAsState(initial = prefs.getBottomMenuStyle())
    val rawBottomMenuBlurEnabled by prefs.bottomMenuBlurFlow().collectAsState(initial = prefs.getBottomMenuBlurEnabled())
    val actualBottomMenuBlurEnabled = bottomMenuStyle == "modern" && rawBottomMenuBlurEnabled

    // On dÃ©termine la page d'accueil de maniÃ¨re instantanÃ©e et synchrone
    var startDestination by remember {
        mutableStateOf(
            when {
                !tokenManager.getAccessToken().isNullOrEmpty() || tokenManager.isGuestMode() -> {
                    val destPref = prefs.getStartDestination()
                    if (destPref == StartDestination.LIBRARY) Screen.Library.route else Screen.Home.route
                }
                else -> Screen.Welcome.route
            }
        )
    }
    var isGuestLoading by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var instantiateWebView by remember { mutableStateOf(false) }

    val isClientIdValid by SessionManager.isClientIdValid.collectAsState()
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
                if (!SessionManager.showCaptchaFlow.value) {
                    SessionManager.requestSessionRefresh(
                        context = context,
                        force = tokenManager.shouldRefreshAccessToken()
                    )
                }
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
            // Respect the showPopups setting for achievements, but always show sleep timer (emoji "🌙")
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

            if (recoveredLogin && (route == Screen.Welcome.route || route == Screen.Login.route)) {
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
            onSearchHandled()
        }
    }

    LaunchedEffect(allAchievementsUnlocked) {
        if (allAchievementsUnlocked) {
            showCompletionScreen = true
        }
    }

    if (instantiateWebView) {
        val showCaptchaWebView by SessionManager.showCaptchaFlow.collectAsState()

        Box(
            modifier = if (showCaptchaWebView) {
                Modifier.fillMaxSize().zIndex(100f)
            } else {
                Modifier.offset(x = (-10000).dp, y = (-10000).dp).size(800.dp).zIndex(-1f)
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        SessionManager.attachGhost(this, ctx)
                    }
                },
                modifier = if (showCaptchaWebView) {
                    Modifier.fillMaxSize().background(Color.White)
                } else {
                    Modifier.fillMaxSize()
                }
            )

            AnimatedVisibility(
                visible = showCaptchaWebView,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Button(
                            onClick = { SessionManager.retryPendingAction() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.captcha_done), fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { SessionManager.cancelCaptcha() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        instantiateWebView = true
        delay(200)
        SessionManager.harvestStoredSession(context)

        val hasToken = !tokenManager.getAccessToken().isNullOrEmpty()

        if (hasToken) {
            SessionManager.requestSessionRefresh(
                context = context,
                force = tokenManager.shouldRefreshAccessToken()
            )
            playerViewModel.fetchUserProfile()
            if (startDestination == Screen.Welcome.route) {
                navigateToAuthenticatedStart()
            }
        }
    }

    LaunchedEffect(isGuestLoading, isClientIdValid) {
        if (isGuestLoading && isClientIdValid) {
            val tm = TokenManager(context)
            tm.setGuestMode(true)
            homeViewModel.loadData()
            delay(500)
            isGuestLoading = false
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Welcome.route) { inclusive = true }
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
                destinationId.startsWith("profile:") -> navController.navigate("profile/${destinationId.removePrefix("profile:")}")
                destinationId.startsWith("tag:") -> navController.navigate("tag/${destinationId.removePrefix("tag:")}")
                destinationId.startsWith("track_detail:") -> navController.navigate("track_detail/${destinationId.removePrefix("track_detail:")}")
                else -> navController.navigate("playlist_detail/$destinationId")
            }
            playerViewModel.onNavigationHandled()
        }
    }

    BackHandler(enabled = playerViewModel.showLyricsSheet) {
        playerViewModel.showLyricsSheet = false
        playerViewModel.isSearchingLyrics = false
    }

    // Update Manager Logic Moved from MainActivity
    val updateStatus by UpdateManager.status.collectAsState()
    val downloadProgress by UpdateManager.downloadProgress.collectAsState()
    val totalDownloadSize by UpdateManager.downloadSize.collectAsState()
    val releaseInfo = UpdateManager.releaseInfo

    LaunchedEffect(updateStatus) {
        if (updateStatus == UpdateStatus.READY_TO_INSTALL) {
            UpdateManager.installUpdate(context.applicationContext)
            UpdateManager.dismiss()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        playerViewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val showBottomUi = !playerViewModel.isPlayerExpanded

        // CORRECTION ICI : On utilise startDestination si currentDestination est null Ã  la frame 1
        val currentRoute = currentDestination?.route ?: startDestination
        val isFullScreenRoute = currentRoute == Screen.Login.route ||
                currentRoute == Screen.Welcome.route ||
                currentRoute == "update"

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
            }
        ) { innerPadding ->

            Box(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val statusBarHeightPx = with(density) {
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
                }
                val bottomBarHeightPx = with(density) { 150.dp.toPx() } // Hauteur de la zone de flou en bas

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .fillMaxSize()
                        .progressiveBlur(
                            blurRadius = 40f,
                            height = statusBarHeightPx * 1.15f,
                            direction = BlurDirection.TOP
                        )
                        .then(
                            if (actualBottomMenuBlurEnabled) {
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
                        slideInHorizontally(initialOffsetX = { it })
                    },
                    exitTransition = {
                        slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
                    },
                    popEnterTransition = {
                        slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                    },
                    popExitTransition = {
                        scaleOut(targetScale = 0.9f) + fadeOut()
                    }
                ) {
                    clippedComposable(Screen.Welcome.route) {
                        WelcomeScreen(
                            onLoginClick = { navController.navigate(Screen.Login.route) },
                            onGuestClick = {
                                isGuestLoading = true
                                scope.launch {
                                    delay(8000)
                                    if (isGuestLoading) {
                                        val tm = TokenManager(context)
                                        tm.setGuestMode(true)
                                        homeViewModel.loadData()
                                        isGuestLoading = false
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Welcome.route) { inclusive = true }
                                        }
                                    }
                                }
                            },
                            isGuestLoading = isGuestLoading
                        )
                    }

                    clippedComposable(Screen.Home.route) {
                        HomeScreen(playerViewModel, homeViewModel, onNavigate = { id ->
                            if (id == "login_required" || id == "my_profile_menu") {
                                showProfileMenu = true
                            } else if (id == "charts") {
                                navController.navigate("charts")
                            } else if (id == "genres") {
                                navController.navigate("genres")
                            } else if (id == "new_releases") {
                                navController.navigate("new_releases")
                            } else if (id.startsWith("profile:")) {
                                navController.navigate("profile/${id.removePrefix("profile:")}")
                            } else if (id.startsWith("profile/")) {
                                navController.navigate(id)
                            } else if (id.startsWith("playlist_detail/")) {
                                navController.navigate(id)
                            } else if (id.startsWith("genre_playlists/")) {
                                navController.navigate(id)
                            }
                            else if (id.startsWith("yt_radio:")) {
                                val rawUrl = id.removePrefix("yt_radio:")
                                val encodedUrl = android.net.Uri.encode(rawUrl)
                                navController.navigate("playlist_detail/yt_radio:$encodedUrl")
                            }
                            else {
                                navController.navigate("playlist_detail/$id")
                            }
                        })
                    }

                    clippedComposable(Screen.Library.route) {
                        LibraryScreen(
                            onLoginClick = { navController.navigate(Screen.Login.route) },
                            onProfileClick = { showProfileMenu = true },
                            onPlaylistClick = { id ->
                                if (id.startsWith("profile:")) {
                                    val userId = id.removePrefix("profile:")
                                    navController.navigate("profile/$userId")
                                } else {
                                    navController.navigate("playlist_detail/$id")
                                }
                            },
                            onLikedTracksClick = { navController.navigate("playlist_detail/likes") },
                            playerViewModel = playerViewModel
                        )
                    }

                    clippedComposable(Screen.Login.route) {
                        LoginScreen({
                            SessionManager.requestSessionRefresh(context, force = true)
                            playerViewModel.fetchUserProfile()
                            homeViewModel.loadData()
                            navController.navigate(Screen.Home.route) { popUpTo(0) }
                        }, { navController.popBackStack() })
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
                                if (route.startsWith("profile:")) {
                                    val userId = route.removePrefix("profile:")
                                    navController.navigate("profile/$userId")
                                } else if (route.startsWith("station_artist:")) {
                                    navController.navigate("playlist_detail/$route")
                                } else {
                                    navController.navigate(route)
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
                                    id.startsWith("profile:") -> {
                                        navController.navigate("profile/${id.removePrefix("profile:")}")
                                    }
                                    id.startsWith("playlist_fans/") -> {
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
                        route = "profile/{userId}",
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) {
                        ProfileScreen(
                            it.arguments?.getString("userId") ?: "",
                            { navController.popBackStack() },
                            playerViewModel,
                            onNavigate = { id ->
                                if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                                else navController.navigate("playlist_detail/$id")
                            }
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
                            navArgument("playlistId") { type = NavType.LongType },
                            navArgument("tabIndex") { type = NavType.IntType; defaultValue = 0 }
                        )
                    ) { entry ->
                        PlaylistFansScreen(
                            playlistId = entry.arguments?.getLong("playlistId") ?: 0L,
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
                            onConversationClick = { otherId, username ->
                                navController.navigate("chat/$otherId/$username")
                            }
                        )
                    }

                    clippedComposable(
                        route = "chat/{otherUserId}/{username}",
                        arguments = listOf(
                            navArgument("otherUserId") { type = NavType.StringType },
                            navArgument("username") { type = NavType.StringType }
                        )
                    ) { entry ->
                        ChatScreen(
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
                        ListeningStatsScreen(
                            onBackClick = { navController.popBackStack() },
                            onTrackClick = { track ->
                                if (track.source == "soundcloud") {
                                    val stubTrack = com.alananasss.kittytune.domain.Track(
                                        id = track.trackId,
                                        title = track.trackTitle,
                                        user = com.alananasss.kittytune.domain.User(0, track.artistName, null),
                                        artworkUrl = track.artworkUrl,
                                        durationMs = 0L
                                    )
                                    playerViewModel.playPlaylist(listOf(stubTrack), 0)
                                }
                            },
                            onArtistClick = { artist ->
                                if (artist.source == "soundcloud") {
                                    playerViewModel.resolveAndNavigateToArtist(artist.artistName, artist.artistId)
                                }
                            }
                        )
                    }

                    clippedComposable("settings") {
                        SettingsScreen(navController, { navController.popBackStack() }, playerViewModel)
                    }

                    clippedComposable("backup_restore") {
                        BackupRestoreScreen(onBackClick = { navController.popBackStack() })
                    }

                    clippedComposable("audio_settings") {
                        AudioSettingsScreen(
                            onBackClick = { navController.popBackStack() },
                            onNavigateToDrmExplanation = { navController.navigate("drm_explanation") },
                            playerViewModel = playerViewModel
                        )
                    }

                    clippedComposable("drm_explanation") {
                        com.alananasss.kittytune.ui.profile.DrmExplanationScreen(
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
                        com.alananasss.kittytune.ui.profile.FabSettingsScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    clippedComposable("color_palette") {
                        ColorPaletteScreen(onBackClick = { navController.popBackStack() })
                    }

                    clippedComposable("about") {
                        AboutScreen(
                            onBackClick = { navController.popBackStack() },
                            onLicensesClick = { navController.navigate("licenses") }
                        )
                    }

                    clippedComposable("licenses") {
                        LicensesScreen(onBackClick = { navController.popBackStack() })
                    }

                    clippedComposable("storage") {
                        StorageScreen(onBackClick = { navController.popBackStack() })
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


                }

                AnimatedVisibility(
                    visible = showBottomUi && !isFullScreenRoute,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val allTabKeys = listOf("home", "search", "genres", "library")
                    val bottomNavItemsKeys = prefs.bottomMenuItemsFlow().collectAsState(initial = prefs.getBottomMenuItems()).value
                    
                    val tabs = allTabKeys.mapNotNull { key ->
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

                    val selectedRoute = tabs.find { tab ->
                        if (tab.route == Screen.Home.route) {
                            currentDestination?.hierarchy?.any { it.route == tab.route } == true && !homeViewModel.isSearching
                        } else if (tab.route == Screen.Search.route) {
                            currentDestination?.hierarchy?.any { it.route == Screen.Home.route } == true && homeViewModel.isSearching
                        } else {
                            currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        }
                    }?.route

                    val fabSetting by prefs.bottomMenuFabFlow().collectAsState(initial = prefs.getBottomMenuFab())
                    val onFabClick: () -> Unit = {
                        when {
                            fabSetting == "settings" -> navController.navigate("settings")
                            fabSetting == "achievements" -> navController.navigate("achievements")
                            fabSetting == "stats" -> navController.navigate("listening_stats")
                            fabSetting == "liked" -> navController.navigate("playlist_detail/likes")
                            fabSetting == "downloads" -> navController.navigate("playlist_detail/downloads")
                            fabSetting == "local" -> navController.navigate("playlist_detail/local_files")
                            fabSetting.startsWith("playlist:") -> {
                                val id = fabSetting.removePrefix("playlist:")
                                navController.navigate("playlist_detail/$id")
                            }
                            else -> showProfileMenu = true
                        }
                    }
                    val fabIcon = when {
                        fabSetting == "settings" -> Icons.Rounded.Settings
                        fabSetting == "achievements" -> Icons.Rounded.EmojiEvents
                        fabSetting == "stats" -> Icons.Rounded.BarChart
                        fabSetting == "liked" -> Icons.Rounded.Favorite
                        fabSetting == "downloads" -> Icons.Rounded.Download
                        fabSetting == "local" -> Icons.Rounded.SdStorage
                        fabSetting.startsWith("playlist:") -> Icons.Rounded.QueueMusic
                        else -> Icons.Rounded.Person
                    }

                    KittyUnifiedBottomBar(
                        tabs = tabs,
                        selectedRoute = selectedRoute,
                        onTabSelected = { tab ->
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
                        },
                        onFabClick = onFabClick,
                        fabIcon = fabIcon,
                        playerViewModel = playerViewModel,
                        onPlayerClick = { playerViewModel.isPlayerExpanded = true },
                        style = bottomMenuStyle,
                        blurEnabled = actualBottomMenuBlurEnabled
                    )
                }
            }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                playerViewModel.currentTrack?.let { track ->
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(80.dp)
                            .alpha(0.4f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(0.6f))
                    )
                }
                LyricsScreen(
                    viewModel = playerViewModel,
                    onClose = {
                        playerViewModel.showLyricsSheet = false
                        playerViewModel.isSearchingLyrics = false
                    }
                )
            }
        }

        if (playerViewModel.showMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { playerViewModel.showMenuSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                MenuSheetContent(playerViewModel)
                Spacer(Modifier.height(32.dp))
            }
        }

        if (playerViewModel.showAddToPlaylistSheet) {
            ModalBottomSheet(
                onDismissRequest = { playerViewModel.showAddToPlaylistSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                AddToPlaylistContent(playerViewModel)
                Spacer(Modifier.height(32.dp))
            }
        }

        if (playerViewModel.showDetailsSheet) {
            ModalBottomSheet(
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
            ModalBottomSheet(
                onDismissRequest = { playerViewModel.showCommentsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                CommentsSheetContent(
                    playerViewModel,
                    { playerViewModel.showCommentsSheet = false }
                )
            }
        }

        if (showProfileMenu) {
            val tokenManager = remember { TokenManager(context) }
            val isGuest = tokenManager.isGuestMode()
            ModalBottomSheet(
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
                    onNotificationsClick = { navController.navigate("notifications") },
                    onMessagesClick = { navController.navigate("conversations") },
                    onAchievementsClick = { navController.navigate("achievements") },
                    onListeningStatsClick = { navController.navigate("listening_stats") },
                    onSettingsClick = { navController.navigate("settings") },
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
                    onDownload = {
                        scope.launch { UpdateManager.downloadUpdate(context.applicationContext) }
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
                                WebView(ctx).apply {
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