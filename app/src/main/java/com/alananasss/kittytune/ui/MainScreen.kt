package com.alananasss.kittytune.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.AchievementManager
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.ui.common.AchievementNotification
import com.alananasss.kittytune.ui.common.AchievementNotificationManager
import com.alananasss.kittytune.ui.common.AchievementPopup
import com.alananasss.kittytune.ui.common.UltimateCompletionOverlay
import com.alananasss.kittytune.ui.home.HomeScreen
import com.alananasss.kittytune.ui.home.HomeScreenShimmer
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.ui.home.TagScreen
import com.alananasss.kittytune.ui.library.LibraryScreen
import com.alananasss.kittytune.ui.library.PlaylistDetailScreen
import com.alananasss.kittytune.ui.login.LoginScreen
import com.alananasss.kittytune.ui.login.WelcomeScreen
import com.alananasss.kittytune.ui.navigation.Screen
import com.alananasss.kittytune.ui.player.*
import com.alananasss.kittytune.ui.player.lyrics.LyricsScreen
import com.alananasss.kittytune.ui.profile.AboutScreen
import com.alananasss.kittytune.ui.profile.AchievementsScreen
import com.alananasss.kittytune.ui.profile.ProfileMenuSheet
import com.alananasss.kittytune.ui.profile.ProfileScreen
import com.alananasss.kittytune.ui.profile.SettingsScreen
import com.alananasss.kittytune.ui.profile.StorageScreen
import com.alananasss.kittytune.ui.track.TrackDetailScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// helps finding the activity from context wrapper
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val bottomNavItems = listOf(Screen.Home, Screen.Library)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val prefs = remember { PlayerPreferences(context) }

    var isAppReady by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf(Screen.Home.route) }
    var isGuestLoading by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }

    val isClientIdValid by SessionManager.isClientIdValid.collectAsState()
    val allAchievementsUnlocked by AchievementManager.isAllUnlocked.collectAsState()
    var showCompletionScreen by remember { mutableStateOf(false) }

    // 1. init state with current value
    var showPopups by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }

    // 2. real-time listener for prefs changes
    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // update state immediately if this key changes
            if (key == "achievement_popups_enabled") {
                showPopups = prefs.getAchievementPopupsEnabled()
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // --- fix: use two state vars to handle exit anim ---
    var currentNotification by remember { mutableStateOf<AchievementNotification?>(null) }
    var animatingNotification by remember { mutableStateOf<AchievementNotification?>(null) }

    // keep data alive during exit animation
    if (currentNotification != null) {
        animatingNotification = currentNotification
    }
    // --- end fix ---

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // refresh stuff when app comes back
                SessionManager.reloadSession()
                showPopups = prefs.getAchievementPopupsEnabled()
                AchievementManager.checkDailyStreak()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showPopups) {
        if (showPopups) {
            AchievementNotificationManager.notifications.collect { notification ->
                currentNotification = notification
                // show popup for 5s
                delay(5000)
                currentNotification = null
            }
        }
    }

    LaunchedEffect(allAchievementsUnlocked) {
        if (allAchievementsUnlocked) {
            showCompletionScreen = true
        }
    }

    // invisible webview to keep session alive
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                visibility = View.GONE
                layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                SessionManager.attachGhost(this, ctx)
            }
        },
        modifier = Modifier.size(1.dp)
    )

    // check login status on startup
    LaunchedEffect(Unit) {
        val tokenManager = TokenManager(context)
        delay(500)

        val hasToken = !tokenManager.getAccessToken().isNullOrEmpty()
        val isGuest = tokenManager.isGuestMode()

        if (hasToken) {
            playerViewModel.fetchUserProfile()
            SessionManager.reloadSession()
            val destPref = prefs.getStartDestination()
            startDestination = if (destPref == StartDestination.LIBRARY) Screen.Library.route else Screen.Home.route
        } else if (isGuest) {
            val destPref = prefs.getStartDestination()
            startDestination = if (destPref == StartDestination.LIBRARY) Screen.Library.route else Screen.Home.route
        } else {
            startDestination = Screen.Welcome.route
        }
        isAppReady = true
    }

    // wait for valid client id before letting guest in
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

    // keep screen awake for lyrics
    if (playerViewModel.showLyricsSheet) {
        DisposableEffect(Unit) {
            val window = context.findActivity()?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    // handle navigation requests from viewmodel
    LaunchedEffect(playerViewModel.navigateToPlaylistId) {
        playerViewModel.navigateToPlaylistId?.let { destinationId ->
            playerViewModel.isPlayerExpanded = false
            when {
                destinationId.startsWith("profile:") -> navController.navigate("profile/${destinationId.removePrefix("profile:")}")
                destinationId.startsWith("tag:") -> navController.navigate("tag/${destinationId.removePrefix("tag:")}")
                destinationId.startsWith("track_detail:") -> navController.navigate("track_detail/${destinationId.removePrefix("track_detail:")}")
                else -> navController.navigate("playlist_detail/$destinationId")
            }
            playerViewModel.onNavigationHandled()
        }
    }

    // close lyrics instead of app
    BackHandler(enabled = playerViewModel.showLyricsSheet) {
        playerViewModel.showLyricsSheet = false
        playerViewModel.isSearchingLyrics = false
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        playerViewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (!isAppReady) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // show skeleton while loading
            Box(modifier = Modifier.statusBarsPadding()) { HomeScreenShimmer() }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val showBottomUi = !playerViewModel.isPlayerExpanded
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isLoginOrWelcome = currentDestination?.route == Screen.Login.route || currentDestination?.route == Screen.Welcome.route

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    // hide nav bar if player is full screen
                    if (!isLoginOrWelcome) {
                        AnimatedVisibility(visible = showBottomUi, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                            NavigationBar(tonalElevation = 0.dp, containerColor = MaterialTheme.colorScheme.surface) {
                                bottomNavItems.forEach { screen ->
                                    if (screen.icon != null) {
                                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleResId)) },
                                            label = { Text(stringResource(screen.titleResId)) },
                                            selected = isSelected,
                                            onClick = {
                                                if (screen.route == Screen.Home.route && homeViewModel.isSearching) {
                                                    homeViewModel.clearSearch()
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id)
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                // navigation graph
                NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.padding(innerPadding)) {
                    composable(Screen.Welcome.route) {
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
                                        navController.navigate(Screen.Home.route) { popUpTo(Screen.Welcome.route) { inclusive = true } }
                                    }
                                }
                            },
                            isGuestLoading = isGuestLoading
                        )
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(playerViewModel, homeViewModel, onNavigate = { id ->
                            if (id == "login_required" || id == "my_profile_menu") {
                                showProfileMenu = true
                            } else if (id.startsWith("profile:")) {
                                navController.navigate("profile/${id.removePrefix("profile:")}")
                            } else {
                                navController.navigate("playlist_detail/$id")
                            }
                        })
                    }
                    composable(Screen.Library.route) {
                        LibraryScreen(
                            onLoginClick = { navController.navigate(Screen.Login.route) },
                            onLogoutClick = { showProfileMenu = true },
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
                    composable(Screen.Login.route) {
                        LoginScreen({
                            playerViewModel.fetchUserProfile()
                            SessionManager.reloadSession()
                            homeViewModel.loadData()
                            navController.navigate(Screen.Home.route) { popUpTo(0) }
                        }, { navController.popBackStack() })
                    }
                    composable("playlist_detail/{playlistId}", arguments = listOf(navArgument("playlistId") { type = NavType.StringType })) {
                        PlaylistDetailScreen(it.arguments?.getString("playlistId") ?: "", { navController.popBackStack() }, playerViewModel)
                    }
                    composable("profile/{userId}", arguments = listOf(navArgument("userId") { type = NavType.StringType })) {
                        ProfileScreen(it.arguments?.getString("userId") ?: "", { navController.popBackStack() }, playerViewModel, onNavigate = { id ->
                            if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                            else navController.navigate("playlist_detail/$id")
                        })
                    }
                    composable("tag/{tagName}", arguments = listOf(navArgument("tagName") { type = NavType.StringType })) {
                        TagScreen(it.arguments?.getString("tagName") ?: "", { navController.popBackStack() }, playerViewModel)
                    }
                    composable("track_detail/{trackId}?tab={tabIndex}", arguments = listOf(navArgument("trackId") { type = NavType.LongType }, navArgument("tabIndex") { type = NavType.IntType; defaultValue = 0 })) {
                        TrackDetailScreen(it.arguments?.getLong("trackId") ?: 0L, it.arguments?.getInt("tabIndex") ?: 0, { navController.popBackStack() }, onNavigate = { id ->
                            if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                            else navController.navigate("playlist_detail/$id")
                        }, playerViewModel)
                    }
                    composable("achievements") { AchievementsScreen { navController.popBackStack() } }
                    composable("settings") { SettingsScreen(navController, { navController.popBackStack() }, playerViewModel) }
                    composable("backup_restore") { com.alananasss.kittytune.ui.profile.BackupRestoreScreen(onBackClick = { navController.popBackStack() }) }
                    composable("audio_settings") { com.alananasss.kittytune.ui.profile.AudioSettingsScreen { navController.popBackStack() } }
                    composable("lyrics_settings") { com.alananasss.kittytune.ui.profile.LyricsSettingsScreen({ navController.popBackStack() }, playerViewModel) }
                    composable("local_media_settings") { com.alananasss.kittytune.ui.profile.LocalMediaSettingsScreen { navController.popBackStack() } }
                    composable("appearance_settings") { com.alananasss.kittytune.ui.profile.AppearanceSettingsScreen { navController.popBackStack() } }
                    composable("about") { AboutScreen { navController.popBackStack() } }
                    composable("storage") { StorageScreen(onBackClick = { navController.popBackStack() }) }
                }

                // show miniplayer above nav bar
                AnimatedVisibility(visible = showBottomUi && playerViewModel.currentTrack != null, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter).padding(innerPadding)) {
                    MiniPlayer(viewModel = playerViewModel, onClick = { playerViewModel.isPlayerExpanded = true })
                }
            }

            // slide up animation for full player
            AnimatedVisibility(visible = playerViewModel.isPlayerExpanded, enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(400)), exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(350)), modifier = Modifier.fillMaxSize()) {
                PlayerScreen(viewModel = playerViewModel, onClose = { playerViewModel.isPlayerExpanded = false })
            }

            // lyrics overlay
            AnimatedVisibility(visible = playerViewModel.showLyricsSheet, enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)), exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)), modifier = Modifier.fillMaxSize().zIndex(10f)) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    playerViewModel.currentTrack?.let { track ->
                        AsyncImage(
                            model = track.fullResArtwork,
                            contentDescription = "",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.4f)
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.6f)))
                    }
                    LyricsScreen(viewModel = playerViewModel, onClose = { playerViewModel.showLyricsSheet = false; playerViewModel.isSearchingLyrics = false })
                }
            }

            // various bottom sheets
            if (playerViewModel.showMenuSheet) { ModalBottomSheet(onDismissRequest = { playerViewModel.showMenuSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { MenuSheetContent(playerViewModel); Spacer(Modifier.height(32.dp)) } }
            if (playerViewModel.showAddToPlaylistSheet) { ModalBottomSheet(onDismissRequest = { playerViewModel.showAddToPlaylistSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { AddToPlaylistContent(playerViewModel); Spacer(Modifier.height(32.dp)) } }
            if (playerViewModel.showDetailsSheet) { ModalBottomSheet(onDismissRequest = { playerViewModel.showDetailsSheet = false }, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) { playerViewModel.selectedTrackForSheet?.let { DetailsSheetContent(it, { playerViewModel.showDetailsSheet = false }, { playerViewModel.openComments() }, playerViewModel) } } }
            if (playerViewModel.showCommentsSheet) { ModalBottomSheet(onDismissRequest = { playerViewModel.showCommentsSheet = false }, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { CommentsSheetContent(playerViewModel, { playerViewModel.showCommentsSheet = false }) } }

            if (showProfileMenu) {
                val tokenManager = remember { TokenManager(context) }
                val isGuest = tokenManager.isGuestMode()
                ModalBottomSheet(onDismissRequest = { showProfileMenu = false }, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    ProfileMenuSheet(homeViewModel.userProfile, isGuest, { showProfileMenu = false }, { homeViewModel.userProfile?.id?.let { navController.navigate("profile/$it") } }, { navController.navigate("achievements") }, { navController.navigate("settings") }, {
                        if (isGuest) {
                            navController.navigate(Screen.Login.route)
                        } else {
                            tokenManager.logout()
                            navController.navigate(Screen.Welcome.route) { popUpTo(0) { inclusive = true } }
                            homeViewModel.loadData()
                        }
                    })
                    Spacer(Modifier.height(32.dp))
                }
            }

            if (showCompletionScreen) { UltimateCompletionOverlay(onDismiss = { showCompletionScreen = false }) }

            AnimatedVisibility(
                visible = currentNotification != null,
                enter = slideInVertically(
                    // drop from top
                    initialOffsetY = { -it },
                    // bouncy spring effect
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(
                    animationSpec = tween(300)
                ) + scaleIn(
                    // slight zoom in
                    initialScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = slideOutVertically(
                    // slide back up fast
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 200)
                ) + scaleOut(
                    // shrink a bit
                    targetScale = 0.8f,
                    animationSpec = tween(durationMillis = 300)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .zIndex(11f)
                    // shadow to make it pop
                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = Color.Black)
            ) {
                // use stable var for exit anim
                animatingNotification?.let {
                    AchievementPopup(notification = it)
                }
            }
        }
    }
}