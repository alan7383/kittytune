package com.alananasss.kittytune

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.alananasss.kittytune.data.AchievementManager
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.HistoryRepository
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.RepostRepository
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.UpdateManager
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.MainScreen
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.utils.Config
import com.alananasss.kittytune.utils.LocaleUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeLocale

    class MainActivity : ComponentActivity() {
    
        override fun attachBaseContext(newBase: Context) {
            super.attachBaseContext(LocaleUtils.updateBaseContextLocale(newBase))
        }
    

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dynamic_theme_enabled" || key == "app_theme_mode" || key == "pure_black_enabled" ||
                key == "custom_font_enabled" || key?.startsWith("font_") == true ||
                key == "key_color" || key == "color_style" || key == "color_spec") {
                refreshThemeState()
            }
        }

        private lateinit var preferences: PlayerPreferences
        private lateinit var sharedPrefs: SharedPreferences
    
        private var themeModeState by mutableStateOf(AppThemeMode.SYSTEM)
        private var dynamicColorState by mutableStateOf(true)
        private var pureBlackState by mutableStateOf(false)
        private var keyColorState by mutableIntStateOf(0)
        private var colorStyleState by mutableStateOf("TonalSpot")
        private var colorSpecState by mutableStateOf("Default")
    
        private val _shouldOpenSearch = MutableStateFlow(false)
        private val shouldOpenSearch = _shouldOpenSearch.asStateFlow()
        private var showPopups by mutableStateOf(false)
        private var customFontEnabledState by mutableStateOf(false)
        private var fontWghtState by mutableIntStateOf(400)
        private var fontWdthState by mutableFloatStateOf(100f)
        private var fontSlntState by mutableFloatStateOf(0f)
        private var fontRondState by mutableFloatStateOf(0f)
        private var fontGradState by mutableFloatStateOf(0f)
        private var fontOpszState by mutableFloatStateOf(14f)
    
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
    
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            )
    
            // Enforce edge-to-edge contrast policies for Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = false
            }
    
            // Initialize Data Layer Singletons
            Config.init(applicationContext)
            LikeRepository.init(applicationContext)
            DownloadManager.init(applicationContext)
            HistoryRepository.init(applicationContext)
            ListeningStatsRepository.init(applicationContext)
            AchievementManager.init(applicationContext)
            RepostRepository.init(applicationContext)
            AchievementManager.resetSessionAchievements()
    
            preferences = PlayerPreferences(applicationContext)
            sharedPrefs = applicationContext.getSharedPreferences("player_state", MODE_PRIVATE)
            sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
    
            val migrationKey = "lyrics_button_default_forced_v1"
            if (!sharedPrefs.getBoolean(migrationKey, false)) {
                preferences.setShowLyricsButtonEnabled(true)
                sharedPrefs.edit { putBoolean(migrationKey, true) }
            }
    
            refreshThemeState()
    
    
            handleIntent(intent)
    
            YouTube.locale = YouTubeLocale(
                gl = "US",
                hl = "en"
            )
            lifecycleScope.launch {
                YouTube.visitorData().onSuccess {
                    YouTube.visitorData = it
                }
            }
    
            setContent {
                val openSearchState by shouldOpenSearch.collectAsState()
                val scope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current
    
                // Global lifecycle observer to sync data when app comes to foreground
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val tokenManager = TokenManager(applicationContext)
                            val hasToken = !tokenManager.getAccessToken().isNullOrEmpty()
                            if (hasToken && !tokenManager.isGuestMode()) {
                                scope.launch {
                                    SessionManager.requestSessionRefresh(
                                        context = applicationContext,
                                        force = tokenManager.shouldRefreshAccessToken()
                                    )
                                    // Sync likes and reposts with server
                                    LikeRepository.setSyncing(true)
                                    RepostRepository.refreshReposts()
                                    DownloadManager.refreshFollowings()
                                }
                            }
                            // Refresh settings state in case changed outside compose
                            showPopups = preferences.getAchievementPopupsEnabled()
                            AchievementManager.checkDailyStreak()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val dynamicTypography = com.alananasss.kittytune.ui.theme.getDynamicTypography(
                    customFontEnabledState, fontWghtState, fontWdthState, fontSlntState, fontRondState, fontGradState, fontOpszState
                )
    
                SoundTuneTheme(
                    themeMode = themeModeState,
                    dynamicColor = dynamicColorState,
                    pureBlack = pureBlackState,
                    keyColor = keyColorState,
                    colorStyle = colorStyleState,
                    colorSpec = colorSpecState,
                    typography = dynamicTypography
                ) {
                    LaunchedEffect(Unit) {
                        if (preferences.getAutoUpdateEnabled()) {
                            UpdateManager.checkForUpdate(applicationContext, isManual = false)
                        }
                    }

    
                    // Main App Content
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainScreen(
                            shouldOpenSearch = openSearchState,
                            onSearchHandled = { _shouldOpenSearch.value = false }
                        )
                    }
                }
            }
        }
    
        override fun onNewIntent(intent: Intent) {
            super.onNewIntent(intent)
            setIntent(intent)
            handleIntent(intent)
        }
    
        private fun handleIntent(intent: Intent?) {
            val openSearch = intent?.getBooleanExtra("open_search", false) ?: false
            if (openSearch) {
                _shouldOpenSearch.value = true
                intent.removeExtra("open_search")
            }

            // Capture SoundCloud OAuth callback redirects
            val data = intent?.data
            if (data != null) {
                val code = data.getQueryParameter("code")
                if (code != null) {
                    com.alananasss.kittytune.data.AuthFlowManager.setAuthCode(code)
                }
            }
        }
    
        private fun refreshThemeState() {
            themeModeState = preferences.getThemeMode()
            dynamicColorState = preferences.getDynamicTheme()
            pureBlackState = preferences.getPureBlack()
            keyColorState = preferences.getKeyColor()
            colorStyleState = preferences.getColorStyle()
            colorSpecState = preferences.getColorSpec()
            customFontEnabledState = preferences.getCustomFontEnabled()
            fontWghtState = preferences.getFontWght()
            fontWdthState = preferences.getFontWdth()
            fontSlntState = preferences.getFontSlnt()
            fontRondState = preferences.getFontRond()
            fontGradState = preferences.getFontGrad()
            fontOpszState = preferences.getFontOpsz()
        }
    
        override fun onDestroy() {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            super.onDestroy()
        }
    }


