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

        val officialCategories = homeViewModel.visualCategories

        val manualCategories: List<com.alananasss.kittytune.data.SearchCategory> = run {
            val officialIds = officialCategories.map { it.id }.toSet()
            (homeViewModel.moodCategories + homeViewModel.genreCategories)
                .filter { it.id !in officialIds && it.artworkUrl.isNullOrBlank() }
                .distinctBy { it.id }
                .sortedBy { it.title }
        }

        val moodCategories = homeViewModel.moodCategories
        val genreCategories = homeViewModel.genreCategories
    }

