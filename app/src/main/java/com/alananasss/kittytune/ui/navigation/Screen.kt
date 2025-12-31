package com.alananasss.kittytune.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.R

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector?) {
    data object Welcome : Screen("welcome", R.string.nav_welcome, null)
    data object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    data object Library : Screen("library", R.string.nav_library, Icons.Default.LibraryMusic)
    data object Login : Screen("login", R.string.nav_login, null)
}