    package com.alananasss.kittytune.ui.home

    import android.app.Application
    import androidx.lifecycle.AndroidViewModel

    class GenresViewModel(application: Application) : AndroidViewModel(application) {
        private val homeViewModel = HomeViewModel(application)

        val moodCategories = homeViewModel.moodCategories
        val genreCategories = homeViewModel.genreCategories
    }

