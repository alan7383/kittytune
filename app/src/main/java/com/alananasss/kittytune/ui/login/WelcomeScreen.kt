package com.alananasss.kittytune.ui.login

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.alananasss.kittytune.ui.player.slider.PlayerSliderTrack
import com.alananasss.kittytune.ui.player.slider.SquigglySlider
import com.alananasss.kittytune.ui.player.slider.WavySlider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerDesign
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import android.content.Intent
import android.provider.Settings
import com.alananasss.kittytune.data.local.PlayerProgressMode
import com.alananasss.kittytune.ui.theme.GoogleSansRounded
import com.alananasss.kittytune.ui.theme.ExpTitleTypography
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

sealed class SetupPage {
    data object Welcome : SetupPage()
    data object AuthMode : SetupPage()
    data object MediaPermission : SetupPage()
    data object NotificationsPermission : SetupPage()
    data object PlayerDesignSelection : SetupPage()
    data object SliderStyleSelection : SetupPage()
    data object ThemeSelection : SetupPage()
    data object Finish : SetupPage()
}

private fun buildSetupPages(sdkInt: Int): List<SetupPage> {
    val pages = mutableListOf<SetupPage>(
        SetupPage.Welcome,
        SetupPage.AuthMode,
        SetupPage.MediaPermission
    )
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        pages += SetupPage.NotificationsPermission
    }
    pages += SetupPage.PlayerDesignSelection
    pages += SetupPage.SliderStyleSelection
    pages += SetupPage.ThemeSelection
    pages += SetupPage.Finish
    return pages
}

private fun hasMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onGuestClick: () -> Unit,
    isGuestLoading: Boolean = false,
    justLoggedIn: Boolean = false,
    onClearJustLoggedIn: () -> Unit = {},
    onSetupComplete: () -> Unit = onGuestClick
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    var isLoggedIn by remember { mutableStateOf(tokenManager.hasAccessToken()) }
    var isMediaGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    var isNotificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var hasAttemptedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLoggedIn = tokenManager.hasAccessToken()
                isMediaGranted = hasMediaPermission(context)
                isNotificationsGranted = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val pages = remember { buildSetupPages(Build.VERSION.SDK_INT) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    var isMediaSkipped by rememberSaveable { mutableStateOf(false) }

    val isPageGateSatisfied: (Int) -> Boolean = { pageIndex ->
        when (pages.getOrNull(pageIndex)) {
            SetupPage.MediaPermission -> isMediaGranted || isMediaSkipped
            SetupPage.NotificationsPermission -> isNotificationsGranted
            else -> true
        }
    }

    val navigateToPage: (Int) -> Unit = { targetPage ->
        scope.launch {
            val boundedPage = targetPage.coerceIn(0, pages.lastIndex)
            if (boundedPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(boundedPage)
            }
        }
    }

    val navigateToNextPage: () -> Unit = {
        if (isPageGateSatisfied(pagerState.currentPage)) {
            navigateToPage(pagerState.currentPage + 1)
        }
    }

    val navigateToPreviousPage: () -> Unit = {
        if (pagerState.currentPage > 0) {
            navigateToPage(pagerState.currentPage - 1)
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it } || hasMediaPermission(context)
        isMediaGranted = granted
        if (granted) {
            scope.launch {
                delay(300)
                navigateToNextPage()
            }
        }
    }

    var hasAutoAdvancedNotification by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val granted = isGranted || hasNotificationPermission(context)
        isNotificationsGranted = granted
        if (granted && !hasAutoAdvancedNotification) {
            hasAutoAdvancedNotification = true
            scope.launch {
                delay(300)
                navigateToNextPage()
            }
        }
    }

    LaunchedEffect(isNotificationsGranted) {
        if (!isNotificationsGranted) {
            hasAutoAdvancedNotification = false
            hasAttemptedNotificationPermission = false
        }
    }

    LaunchedEffect(justLoggedIn) {
        if (justLoggedIn) {
            isLoggedIn = tokenManager.hasAccessToken()
            val authPageIndex = pages.indexOf(SetupPage.AuthMode)
            if (authPageIndex >= 0) {
                delay(300)
                val targetPage = authPageIndex + 1
                if (targetPage < pages.size) {
                    pagerState.animateScrollToPage(targetPage)
                }
            }
            onClearJustLoggedIn()
        }
    }

    LaunchedEffect(isNotificationsGranted) {
        val notifPageIndex = pages.indexOf(SetupPage.NotificationsPermission)
        if (notifPageIndex >= 0 && isNotificationsGranted && pagerState.currentPage == notifPageIndex && hasAttemptedNotificationPermission && !hasAutoAdvancedNotification) {
            hasAutoAdvancedNotification = true
            delay(300)
            navigateToNextPage()
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        navigateToPreviousPage()
    }

    var previousPageIndex by rememberSaveable { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > previousPageIndex) {
            val blockedPageIndex = (previousPageIndex until pagerState.currentPage).firstOrNull { pageIdx ->
                !isPageGateSatisfied(pageIdx)
            }
            if (blockedPageIndex != null) {
                pagerState.scrollToPage(blockedPageIndex)
                previousPageIndex = blockedPageIndex
                return@LaunchedEffect
            }
        }
        previousPageIndex = pagerState.currentPage
    }

    var selectedPlayerDesign by remember { mutableStateOf(prefs.getPlayerDesign()) }
    var selectedSliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }
    var selectedThemeMode by remember { mutableStateOf(prefs.getThemeMode()) }

    val isCurrentPageGateSatisfied = isPageGateSatisfied(pagerState.currentPage)

    val layoutDirection = LocalLayoutDirection.current
    val forwardScrollBlocker = remember(layoutDirection, pagerState, pages, isMediaGranted, isMediaSkipped, isNotificationsGranted) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isForward = if (layoutDirection == LayoutDirection.Ltr) available.x < 0f else available.x > 0f
                if (isForward) {
                    val currentPage = pagerState.currentPage
                    val isGateSatisfied = isPageGateSatisfied(currentPage)
                    val offsetFraction = pagerState.currentPageOffsetFraction
                    val isAtOrBeyondPage = if (layoutDirection == LayoutDirection.Ltr) {
                        offsetFraction >= -0.001f
                    } else {
                        offsetFraction <= 0.001f
                    }
                    if (!isGateSatisfied && isAtOrBeyondPage) {
                        return Offset(available.x, 0f)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val isForward = if (layoutDirection == LayoutDirection.Ltr) available.x < 0f else available.x > 0f
                if (isForward) {
                    val currentPage = pagerState.currentPage
                    val isGateSatisfied = isPageGateSatisfied(currentPage)
                    val offsetFraction = pagerState.currentPageOffsetFraction
                    val isAtOrBeyondPage = if (layoutDirection == LayoutDirection.Ltr) {
                        offsetFraction >= -0.001f
                    } else {
                        offsetFraction <= 0.001f
                    }
                    if (!isGateSatisfied && isAtOrBeyondPage) {
                        return Velocity(available.x, 0f)
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Scaffold(
        bottomBar = {
            SetupBottomBar(
                pagerState = pagerState,
                onNextClicked = navigateToNextPage,
                onBackClicked = navigateToPreviousPage,
                onFinishClicked = {
                    onSetupComplete()
                },
                isNextButtonEnabled = isCurrentPageGateSatisfied,
                isFinishButtonEnabled = true
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(forwardScrollBlocker)
        ) { pageIndex ->
            when (pages[pageIndex]) {
                SetupPage.Welcome -> {
                    WelcomePage()
                }

                SetupPage.AuthMode -> {
                    AuthModePage(
                        isLoggedIn = isLoggedIn,
                        onSoundCloudClick = onLoginClick,
                        onGuestChosen = {
                            navigateToNextPage()
                        }
                    )
                }

                SetupPage.MediaPermission -> {
                    PermissionPageLayout(
                        title = stringResource(R.string.setup_permission_media_title),
                        description = stringResource(R.string.setup_permission_media_description),
                        granted = isMediaGranted,
                        buttonText = if (isMediaGranted) {
                            stringResource(R.string.setup_permission_granted)
                        } else {
                            stringResource(R.string.setup_grant_media_permission)
                        },
                        buttonEnabled = !isMediaGranted,
                        icons = listOf(
                            R.drawable.rounded_music_note_24,
                            R.drawable.rounded_album_24,
                            R.drawable.rounded_library_music_24,
                            R.drawable.rounded_artist_24,
                            R.drawable.rounded_playlist_play_24
                        ),
                        onGrantClicked = {
                            if (!isMediaGranted) {
                                mediaLauncher.launch(mediaPermissions)
                            }
                        },
                        content = {
                            if (!isMediaGranted) {
                                TextButton(
                                    onClick = {
                                        isMediaSkipped = true
                                        navigateToNextPage()
                                    },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(stringResource(R.string.setup_skip_for_now))
                                }
                            }
                        }
                    )
                }

                SetupPage.NotificationsPermission -> {
                    PermissionPageLayout(
                        title = stringResource(R.string.setup_permission_notifications_title),
                        description = stringResource(R.string.setup_permission_notifications_description),
                        granted = isNotificationsGranted,
                        buttonText = if (isNotificationsGranted) {
                            stringResource(R.string.setup_permission_granted)
                        } else {
                            stringResource(R.string.setup_enable_notifications)
                        },
                        buttonEnabled = !isNotificationsGranted,
                        icons = listOf(
                            R.drawable.rounded_circle_notifications_24,
                            R.drawable.ic_skip_next,
                            R.drawable.ic_play_arrow,
                            R.drawable.ic_pause,
                            R.drawable.ic_skip_previous
                        ),
                        onGrantClicked = {
                            if (!isNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val activity = context as? androidx.activity.ComponentActivity
                                val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
                                if (hasAttemptedNotificationPermission && shouldShowRationale) {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    hasAttemptedNotificationPermission = true
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    )
                }

                SetupPage.PlayerDesignSelection -> {
                    PlayerDesignSelectionPage(
                        selectedDesign = selectedPlayerDesign,
                        onDesignSelected = { design ->
                            selectedPlayerDesign = design
                            prefs.setPlayerDesign(design)
                            if (design == PlayerDesign.MODERN) {
                                prefs.setPlayerProgressMode(PlayerProgressMode.CLASSIC_BAR)
                            }
                        }
                    )
                }

                SetupPage.SliderStyleSelection -> {
                    SliderStyleSelectionPage(
                        selectedStyle = selectedSliderStyle,
                        onStyleSelected = { style ->
                            selectedSliderStyle = style
                            prefs.setPlayerSliderStyle(style)
                        }
                    )
                }

                SetupPage.ThemeSelection -> {
                    ThemeSelectionPage(
                        selectedTheme = selectedThemeMode,
                        onThemeSelected = { theme ->
                            selectedThemeMode = theme
                            prefs.setThemeMode(theme)
                        }
                    )
                }

                SetupPage.Finish -> {
                    FinishPage(
                        onFinishClick = onSetupComplete
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Each line is meant to stay on one line. "Welcome to " fits at 42.sp,
            // but longer translations (German "Willkommen bei ") did not and were
            // broken mid-word. Auto-sizing keeps the original size where it fits.
            Text(
                text = stringResource(R.string.setup_welcome_prefix),
                modifier = Modifier.fillMaxWidth(),
                style = ExpTitleTypography.displayLarge.copy(
                    fontSize = 42.sp,
                    lineHeight = 1.1.em
                ),
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 26.sp,
                    maxFontSize = 42.sp,
                    stepSize = 1.sp
                ),
                maxLines = 1
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = GoogleSansRounded,
                    fontSize = 46.sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 1.1.em
                ),
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 28.sp,
                    maxFontSize = 46.sp,
                    stepSize = 1.sp
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_beta_symbol),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.setup_beta_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            com.alananasss.kittytune.ui.common.MaterialYouVectorDrawable(
                modifier = Modifier.fillMaxSize(),
                drawableResId = R.drawable.welcome_art
            )
            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.surface,
                alpha = 0.95f,
                strokeWidth = 16.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(22.dp)
                    .background(color = MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp)
            )
            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.primary,
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.setup_intro_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AuthModePage(
    isLoggedIn: Boolean = false,
    onSoundCloudClick: () -> Unit,
    onGuestChosen: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupPageTitle(text = stringResource(R.string.setup_auth_title))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AuthModeOptionCard(
                title = stringResource(R.string.setup_auth_soundcloud_title),
                description = stringResource(R.string.setup_auth_soundcloud_desc),
                badgeText = if (isLoggedIn) {
                    stringResource(R.string.account_connected_status)
                } else {
                    stringResource(R.string.setup_auth_soundcloud_badge)
                },
                isSelected = isLoggedIn,
                onClick = if (isLoggedIn) onGuestChosen else onSoundCloudClick,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_soundcloud),
                        contentDescription = null,
                        tint = if (isLoggedIn) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.width(28.dp)
                    )
                }
            )

            AuthModeOptionCard(
                title = stringResource(R.string.setup_auth_guest_title),
                description = stringResource(R.string.setup_auth_guest_desc),
                badgeText = stringResource(R.string.setup_auth_guest_badge),
                isSelected = false,
                onClick = onGuestChosen,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.setup_auth_footer_hint),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AuthModeOptionCard(
    title: String,
    description: String,
    badgeText: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(200),
        label = "AuthCardContainerColor"
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (badgeText != null) {
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionPageLayout(
    title: String,
    description: String,
    buttonText: String,
    icons: List<Int>,
    granted: Boolean = false,
    buttonEnabled: Boolean = true,
    onGrantClicked: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupPageTitle(text = title)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            contentAlignment = Alignment.Center
        ) {
            PermissionIconCollage(
                modifier = Modifier.height(210.dp),
                icons = icons
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantClicked,
                enabled = buttonEnabled,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                AnimatedContent(targetState = granted, label = "ButtonAnim") { isGranted ->
                    if (isGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(buttonText, style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// -----------------------------------------------------------------------------------------
// Selection Indicator Component (PixelPlayer radio-style circle)
// -----------------------------------------------------------------------------------------

@Composable
private fun SetupSelectionIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = CircleShape,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Page 4: Player Design Selection
// -----------------------------------------------------------------------------------------

private data class PlayerDesignItem(
    val design: PlayerDesign,
    val titleRes: Int,
    val descRes: Int,
    val badge: String,
    @DrawableRes val iconRes: Int
)

@Composable
private fun PlayerDesignSelectionPage(
    selectedDesign: PlayerDesign,
    onDesignSelected: (PlayerDesign) -> Unit
) {
    val options = remember {
        listOf(
            PlayerDesignItem(
                design = PlayerDesign.PIXEL_PLAYER,
                titleRes = R.string.setup_player_design_pixel,
                descRes = R.string.setup_player_design_pixel_desc,
                badge = "Pixel UI",
                iconRes = R.drawable.rounded_music_note_24
            ),
            PlayerDesignItem(
                design = PlayerDesign.SOUNDCLOUD,
                titleRes = R.string.setup_player_design_soundcloud,
                descRes = R.string.setup_player_design_soundcloud_desc,
                badge = "SoundCloud",
                iconRes = R.drawable.ic_soundcloud
            ),
            PlayerDesignItem(
                design = PlayerDesign.MODERN,
                titleRes = R.string.setup_player_design_modern,
                descRes = R.string.setup_player_design_modern_desc,
                badge = "Moderne",
                iconRes = R.drawable.ic_kittytune_logo
            ),
            PlayerDesignItem(
                design = PlayerDesign.CLASSIC,
                titleRes = R.string.setup_player_design_classic,
                descRes = R.string.setup_player_design_classic_desc,
                badge = "Classique",
                iconRes = R.drawable.rounded_library_music_24
            )
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupPageTitle(text = stringResource(R.string.setup_player_design_title))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_player_design_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { item ->
                val isSelected = selectedDesign == item.design
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    animationSpec = tween(200),
                    label = "PlayerCardColor"
                )

                Card(
                    onClick = { onDesignSelected(item.design) },
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = if (item.iconRes == R.drawable.ic_soundcloud) {
                                        Modifier.width(28.dp)
                                    } else {
                                        Modifier.size(24.dp)
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(item.titleRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = item.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(item.descRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SetupSelectionIndicator(isSelected = isSelected)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.setup_player_design_footer),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// -----------------------------------------------------------------------------------------
// Page 5: Progress Slider Style Selection
// -----------------------------------------------------------------------------------------

private data class SliderStyleItem(
    val style: PlayerSliderStyle,
    @StringRes val titleRes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderStyleSelectionPage(
    selectedStyle: PlayerSliderStyle,
    onStyleSelected: (PlayerSliderStyle) -> Unit
) {
    val styles = remember {
        listOf(
            SliderStyleItem(PlayerSliderStyle.WAVY, R.string.slider_style_wavy),
            SliderStyleItem(PlayerSliderStyle.BAR, R.string.slider_style_bar),
            SliderStyleItem(PlayerSliderStyle.SLIM, R.string.slider_style_slim),
            SliderStyleItem(PlayerSliderStyle.SQUIGGLY, R.string.slider_style_squiggly)
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupPageTitle(text = stringResource(R.string.setup_slider_style_title))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_slider_style_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            styles.forEach { item ->
                val isSelected = selectedStyle == item.style

                Card(
                    onClick = { onStyleSelected(item.style) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(item.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            SetupSelectionIndicator(isSelected = isSelected)
                        }

                        val previewThumbColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        val previewActiveTrackColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        val previewInactiveTrackColor = (if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.22f)

                        val previewColors = SliderDefaults.colors(
                            thumbColor = previewThumbColor,
                            activeTrackColor = previewActiveTrackColor,
                            inactiveTrackColor = previewInactiveTrackColor,
                            disabledThumbColor = previewThumbColor,
                            disabledActiveTrackColor = previewActiveTrackColor,
                            disabledInactiveTrackColor = previewInactiveTrackColor,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (item.style) {
                                PlayerSliderStyle.BAR -> {
                                    Slider(
                                        value = 0.35f,
                                        valueRange = 0f..1f,
                                        onValueChange = {},
                                        colors = previewColors,
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                PlayerSliderStyle.WAVY -> {
                                    WavySlider(
                                        value = 0.5f,
                                        valueRange = 0f..1f,
                                        onValueChange = {},
                                        colors = previewColors,
                                        isPlaying = true,
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                PlayerSliderStyle.SLIM -> {
                                    val slimState = remember { SliderState(0.65f, 0, 0f..1f) }
                                    Slider(
                                        state = slimState,
                                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                        track = { sliderState ->
                                            PlayerSliderTrack(
                                                sliderState = sliderState,
                                                colors = previewColors,
                                                trackHeight = 10.dp
                                            )
                                        },
                                        colors = previewColors,
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                PlayerSliderStyle.SQUIGGLY -> {
                                    SquigglySlider(
                                        value = 0.5f,
                                        valueRange = 0f..1f,
                                        onValueChange = {},
                                        colors = previewColors,
                                        isPlaying = true,
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.setup_slider_style_footer),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// -----------------------------------------------------------------------------------------
// Page 6: App Theme Mode Selection
// -----------------------------------------------------------------------------------------

private data class ThemeOptionItem(
    val mode: AppThemeMode,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val recommended: Boolean = false
)

@Composable
private fun ThemeSelectionPage(
    selectedTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit
) {
    val themeOptions = remember {
        listOf(
            ThemeOptionItem(
                mode = AppThemeMode.DARK,
                titleRes = R.string.setup_theme_dark_title,
                descRes = R.string.setup_theme_dark_description,
                icon = Icons.Rounded.DarkMode,
                recommended = true
            ),
            ThemeOptionItem(
                mode = AppThemeMode.LIGHT,
                titleRes = R.string.setup_theme_light_title,
                descRes = R.string.setup_theme_light_description,
                icon = Icons.Outlined.LightMode
            ),
            ThemeOptionItem(
                mode = AppThemeMode.SYSTEM,
                titleRes = R.string.setup_theme_follow_title,
                descRes = R.string.setup_theme_follow_description,
                icon = Icons.Rounded.PhoneAndroid
            )
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupPageTitle(text = stringResource(R.string.setup_theme_title))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_theme_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            themeOptions.forEach { option ->
                val isSelected = selectedTheme == option.mode
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    animationSpec = tween(200),
                    label = "ThemeCardColor"
                )

                Card(
                    onClick = { onThemeSelected(option.mode) },
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(option.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (option.recommended) {
                                Surface(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.setup_recommended),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(option.descRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SetupSelectionIndicator(isSelected = isSelected)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.setup_theme_footer),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FinishPage(
    onFinishClick: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    val finishIcons = remember {
        listOf(
            R.drawable.rounded_check_circle_24,
            R.drawable.round_favorite_24,
            R.drawable.rounded_celebration_24,
            R.drawable.round_favorite_24,
            R.drawable.rounded_explosion_24
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupPageTitle(text = stringResource(R.string.setup_all_set_title))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.setup_all_set_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            PermissionIconCollage(
                modifier = Modifier.height(230.dp),
                icons = finishIcons
            )
        }

        Surface(
            onClick = { uriHandler.openUri("https://ko-fi.com/alan7383") },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_kofi_symbol),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.setup_support_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.setup_support_desc_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


/**
 * Title for a setup page.
 *
 * The setup titles used a fixed 32.sp, which only fits the English strings. Longer
 * translations (German "Wiedergabebenachrichtigungen", Hungarian, Russian) overflowed
 * and Compose broke them mid-word. Auto-sizing keeps 32.sp whenever the text fits, so
 * English is unchanged, and steps down only as far as a longer translation needs.
 */
@Composable
private fun SetupPageTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.displayMedium.copy(
            fontFamily = GoogleSansRounded,
            fontSize = 32.sp
        ),
        autoSize = TextAutoSize.StepBased(
            minFontSize = 20.sp,
            maxFontSize = 32.sp,
            stepSize = 1.sp
        ),
        maxLines = 2,
        textAlign = TextAlign.Center
    )
}
