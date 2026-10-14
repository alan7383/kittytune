package com.alananasss.kittytune.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A chart is a list of songs in order, so the two things worth choosing between are which list and
 * which genre.
 *
 * The two lists are not the same request, which is worth stating because it was not obvious:
 * `GET /charts` answers only `kind=trending`. `kind=top` comes back as an empty object, with or
 * without a genre, and so does every genre other than `all-music` — the endpoint narrowed to one
 * live feed. The Top 50 is a different thing entirely: it is a playlist SoundCloud curates by
 * hand and publishes at `soundcloud.com/music-charts-<country>/sets/<slug>`, fifty tracks in
 * order, which `/resolve` hands back whole. So the genre row belongs to [ChartKind.TOP] and the
 * trending feed has no genre to pick.
 */
enum class ChartKind {
    /**
     * What is played most in the chosen country: Spotify's own Top 50 where it keeps one, else Deezer's
     * chart for the country. SoundCloud publishes charts for a handful of countries only, so its own were a
     * US list for everyone else (issue #66).
     */
    COUNTRY,
    TOP,
    TRENDING,
}

/** The label resource of a chart kind. */
fun ChartKind.labelKey(): Int = when (this) {
    ChartKind.COUNTRY -> R.string.chart_kind_country
    ChartKind.TOP -> R.string.chart_kind_top
    ChartKind.TRENDING -> R.string.chart_kind_trending
}

/**
 * A chart playlist, by the slug SoundCloud publishes it under.
 *
 * [slug] is the path segment after `/sets/`; the country is the `music-charts-<cc>` account, which
 * is why the same genre has a different chart in a different market.
 */
data class ChartGenre(val id: String, val slug: String)

/**
 * A country whose chart can be chosen. One for each language the app is translated into, plus the UK; the
 * name is what Deezer calls the country in its "Top <country>" playlists, the storefront code is the country
 * SoundCloud's own chart account spells it with.
 *
 * @param hasDeezerChart false where Deezer stopped keeping one: it left Russia, and its "Top Russia" has held a
 *   single track since, so that chart comes from Spotify or the trending feed straight away (issue #66).
 */
enum class ChartCountry(
    val code: String,
    val deezerName: String,
    val language: String,
    val storefront: String,
    val hasDeezerChart: Boolean = true,
) {
    RU("RU", "Russia", "ru", "ru", hasDeezerChart = false),
    US("US", "United States", "en", "us"),
    UK("UK", "United Kingdom", "en", "gb"),
    DE("DE", "Germany", "de", "de"),
    FR("FR", "France", "fr", "fr"),
    HU("HU", "Hungary", "hu", "hu"),
    VN("VN", "Vietnam", "vi", "vn"),
    // Countries where Spotify keeps its own Top 50, so the chart opens at once. No Deezer chart is assumed for them:
    // Deezer answers an unknown country with its world chart, which would pass for a local one.
    FI("FI", "Finland", "fi", "fi", hasDeezerChart = false),
    SE("SE", "Sweden", "sv", "se", hasDeezerChart = false),
    NO("NO", "Norway", "no", "no", hasDeezerChart = false),
    PL("PL", "Poland", "pl", "pl", hasDeezerChart = false),
    IT("IT", "Italy", "it", "it", hasDeezerChart = false),
    ES("ES", "Spain", "es", "es", hasDeezerChart = false),
    NL("NL", "Netherlands", "nl", "nl", hasDeezerChart = false),
    CA("CA", "Canada", "en", "ca", hasDeezerChart = false),
    AU("AU", "Australia", "en", "au", hasDeezerChart = false),
    JP("JP", "Japan", "ja", "jp", hasDeezerChart = false),
    BR("BR", "Brazil", "pt", "br", hasDeezerChart = false),
    MX("MX", "Mexico", "es", "mx", hasDeezerChart = false),
    IE("IE", "Ireland", "en", "ie", hasDeezerChart = false),
    AT("AT", "Austria", "de", "at", hasDeezerChart = false),
    CZ("CZ", "Czech Republic", "cs", "cz", hasDeezerChart = false);

    companion object {
        /** The country whose language the app is in. */
        fun forLanguage(language: String): ChartCountry = entries.firstOrNull { it.language == language } ?: US

        /**
         * The country the device is set to: the region of the system locale, not the language of the
         * app. A Russian interface in Finland is a listener in Finland, and the chart for "your
         * country" is Finland's.
         */
        fun forDevice(language: String): ChartCountry {
            val region = Locale.getDefault().country.uppercase()
            val code = if (region == "GB") "UK" else region
            return entries.firstOrNull { it.code == code } ?: forLanguage(language)
        }
    }
}

/** One song at its place in the chart. [rank] is the position, never the score. */
data class ChartEntry(val rank: Int, val track: Track, val score: Double)

class ChartsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)

    var isLoading by mutableStateOf(false)

    // ── The song chart ──
    var chartKind by mutableStateOf(ChartKind.COUNTRY)
    var chartGenre by mutableStateOf(chartGenres.first())
    var chartCountry by mutableStateOf(ChartCountry.forDevice(Locale.getDefault().language))
    val chartEntries = mutableStateListOf<ChartEntry>()
    var isChartLoading by mutableStateOf(false)

    init {
        loadChart(ChartKind.COUNTRY, chartGenres.first())
    }

    private var chartJob: Job? = null

    /**
     * Loads the ranked song list.
     *
     * The rank is the row's position and never the score: on the trending feed the score is a
     * velocity, not a size, and re-sorting it would be second-guessing an order the server has
     * already decided.
     */
    fun loadChart(kind: ChartKind, genre: ChartGenre) {
        chartKind = kind
        chartGenre = genre

        // A switch made mid-flight is cancelled, not raced: when only kind and genre were compared, a slow answer for
        // the country picked before this one could land after the new one and put the old country's songs back.
        chartJob?.cancel()
        val country = chartCountry
        chartJob = viewModelScope.launch {
            isChartLoading = true
            val entries = fetchChart(api, kind, genre, CHART_LENGTH, currentCountryCode(), country)
            chartEntries.clear()
            chartEntries.addAll(entries)
            isChartLoading = false
        }
    }

    fun selectChartCountry(country: ChartCountry) {
        chartCountry = country
        loadChart(chartKind, chartGenre)
    }

    /** The picked country as SoundCloud's own chart playlists spell it. */
    fun currentCountryCode(): String = chartCountry.storefront.uppercase()

    companion object {
        /** A chart long enough to feel like one. SoundCloud's own are 50 and 500. */
        const val CHART_LENGTH = 50

        val chartGenres = listOf(
            ChartGenre("all", "all-music-genres"),
            ChartGenre("pop", "pop"),
            ChartGenre("hiphop", "hip-hop"),
            ChartGenre("electronic", "electronic"),
            ChartGenre("rock", "rock"),
            ChartGenre("rnb", "r-b"),
            ChartGenre("country", "country"),
            ChartGenre("latin", "latin"),
            ChartGenre("folk", "folk"),
        )

        /** The curated chart playlist URL, for a country code as the chart account spells it. */
        fun chartPlaylistUrl(countryCode: String, genre: ChartGenre): String =
            "https://soundcloud.com/music-charts-${countryCode.lowercase()}/sets/${genre.slug}"
    }
}
