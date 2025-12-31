package com.alananasss.kittytune

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.alananasss.kittytune.data.AchievementManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.HistoryRepository
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.MainScreen
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.utils.Config
import com.alananasss.kittytune.utils.LocaleUtils

class MainActivity : ComponentActivity() {

    // --- setup language ---
    // called before onCreate, this is where we force the locale
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.updateBaseContextLocale(newBase))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    // listen for pref changes to update theme instantly
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "dynamic_theme_enabled" || key == "app_theme_mode" || key == "pure_black_enabled") {
            refreshThemeState()
        }
    }

    private lateinit var preferences: PlayerPreferences
    private lateinit var sharedPrefs: SharedPreferences

    // observable states to trigger theme recomposition
    private var themeModeState by mutableStateOf(AppThemeMode.SYSTEM)
    private var dynamicColorState by mutableStateOf(true)
    private var pureBlackState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // --- init stuff ---
        Config.init(applicationContext)
        LikeRepository.init(applicationContext)
        DownloadManager.init(applicationContext)
        HistoryRepository.init(applicationContext)
        AchievementManager.init(applicationContext)

        // init prefs and listener
        preferences = PlayerPreferences(applicationContext)
        sharedPrefs = applicationContext.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)

        // load initial state
        refreshThemeState()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // pass states to the theme
            SoundTuneTheme(
                themeMode = themeModeState,
                dynamicColor = dynamicColorState,
                pureBlack = pureBlackState
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }

    private fun refreshThemeState() {
        themeModeState = preferences.getThemeMode()
        dynamicColorState = preferences.getDynamicTheme()
        pureBlackState = preferences.getPureBlack()
    }

    override fun onDestroy() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }
}