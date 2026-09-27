    package com.alananasss.kittytune.ui.home

    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import com.alananasss.kittytune.data.local.PlayerPreferences

    class GenresViewModel(application: Application) : AndroidViewModel(application) {
        private val homeViewModel = HomeViewModel(application)
        private val prefs = PlayerPreferences(application)

        var isGridLayout by mutableStateOf(prefs.getExplorerGridLayout())
            private set

        fun toggleLayout() {
            val next = !isGridLayout
            isGridLayout = next
            prefs.setExplorerGridLayout(next)
        }

        val moodCategories = homeViewModel.moodCategories
        val genreCategories = homeViewModel.genreCategories
    }

