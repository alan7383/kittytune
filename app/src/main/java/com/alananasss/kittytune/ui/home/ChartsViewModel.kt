package com.alananasss.kittytune.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The charts screen's state (issue #66, ported from desktop): one kind of chart at a time, for one
 * country, one genre; a late answer for an old country must never replace the new one's, so the
 * loading job is cancelled and the current kind re-checked when the answer lands.
 */
class ChartsViewModel(application: Application) : AndroidViewModel(application) {

    var chartKind by mutableStateOf(ChartRepository.ChartKind.COUNTRY)
        private set
    var chartGenre by mutableStateOf(ChartRepository.chartGenres.first())
        private set
    var chartCountry by mutableStateOf(
        ChartRepository.ChartCountry.forDevice(
            application,
            run {
                val appLang = PlayerPreferences(application).getAppLanguage()
                if (appLang == com.alananasss.kittytune.data.local.AppLanguage.SYSTEM)
                    java.util.Locale.getDefault().language
                else appLang.code
            }
        )
    )
        private set

    val chartEntries = mutableStateListOf<ChartRepository.ChartEntry>()
    var isChartLoading by mutableStateOf(false)
        private set

    private var chartJob: Job? = null

    init {
        loadChart(chartKind, chartGenre)
    }

    fun loadChart(kind: ChartRepository.ChartKind, genre: ChartRepository.ChartGenre) {
        chartKind = kind
        chartGenre = genre
        chartJob?.cancel()
        val country = chartCountry
        val countryCode = currentCountryCode()
        chartJob = viewModelScope.launch {
            isChartLoading = true
            val entries = ChartRepository.fetchChart(
                getApplication(), kind, genre, CHART_LENGTH, countryCode,
                if (kind == ChartRepository.ChartKind.COUNTRY) country else null,
            )
            chartEntries.clear()
            chartEntries.addAll(entries)
            isChartLoading = false
        }
    }

    fun selectChartKind(kind: ChartRepository.ChartKind) = loadChart(kind, chartGenre)

    fun selectChartCountry(country: ChartRepository.ChartCountry) {
        chartCountry = country
        loadChart(ChartRepository.ChartKind.COUNTRY, chartGenre)
    }

    fun selectChartGenre(genre: ChartRepository.ChartGenre) = loadChart(ChartRepository.ChartKind.TOP, genre)

    /** The SoundCloud curated playlists only exist for the US and the UK. */
    fun currentCountryCode(): String = if (chartCountry == ChartRepository.ChartCountry.UK) "UK" else "US"

    companion object {
        const val CHART_LENGTH = 50
    }
}
