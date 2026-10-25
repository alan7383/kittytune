package com.alananasss.kittytune.ui.home

import android.content.Context
import android.telephony.TelephonyManager
import com.alananasss.kittytune.data.deezer.DeezerSearchRepository
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.SafeTrackListAdapter
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.AppUtils
import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The charts (issue #66 on desktop, ported): what a country is listening to, as one queue.
 *
 * A country chart is the Spotify Top 50 playlist for that country — found by search, not hardcoded, since
 * Spotify renames and re-ids them — so the songs play as they are. Spotify-only countries fall back to
 * Deezer's own chart playlists; SoundCloud's curated US/UK playlists and its trending feed cover the rest.
 * Whatever came back is ranked by its position in the source, never re-sorted, and cached on disk for six
 * hours so the chart does not move under the listener's feet while they browse it.
 */
object ChartRepository {

    data class ChartEntry(val rank: Int, val track: Track)

    enum class ChartKind { COUNTRY, TOP, TRENDING }

    data class ChartGenre(val id: String, val slug: String)

    enum class ChartCountry(
        val code: String,
        val deezerName: String,
        val language: String,
    ) {
        RU("RU", "Russia", "ru"),
        US("US", "United States", "en"),
        UK("UK", "United Kingdom", "en"),
        DE("DE", "Germany", "de"),
        FR("FR", "France", "fr"),
        HU("HU", "Hungary", "hu"),
        VN("VN", "Vietnam", "vi"),
        FI("FI", "Finland", "en"),
        SE("SE", "Sweden", "en"),
        NO("NO", "Norway", "en"),
        PL("PL", "Poland", "en"),
        IT("IT", "Italy", "en"),
        ES("ES", "Spain", "en"),
        NL("NL", "Netherlands", "en"),
        CA("CA", "Canada", "en"),
        AU("AU", "Australia", "en"),
        JP("JP", "Japan", "en"),
        BR("BR", "Brazil", "en"),
        MX("MX", "Mexico", "en"),
        IE("IE", "Ireland", "en"),
        AT("AT", "Austria", "en"),
        CZ("CZ", "Czechia", "en");

        companion object {
            fun forLanguage(language: String): ChartCountry =
                entries.firstOrNull { it.language.equals(language, true) } ?: US

            /** The device's country when it has one the charts cover, else the language's home, else US. */
            fun forDevice(context: Context, language: String): ChartCountry {
                val region = DeviceRegion.code(context)
                val code = if (region == "GB") "UK" else region
                return entries.firstOrNull { it.code == code } ?: forLanguage(language)
            }
        }
    }

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

    /** The SoundCloud curated chart playlist, which exists only for the US and the UK. */
    fun chartPlaylistUrl(countryCode: String, genre: ChartGenre): String =
        "https://soundcloud.com/music-charts-${if (countryCode == "UK") "uk" else "us"}/sets/${genre.slug}"

    /**
     * The chart of [kind] for [country], as ranked entries. Rank is always the position in the source,
     * never a score: what the listener sees is what the country is listening to, in its order.
     */
    suspend fun fetchChart(
        context: Context,
        kind: ChartKind,
        genre: ChartGenre,
        limit: Int,
        countryCode: String,
        country: ChartCountry? = null,
    ): List<ChartEntry> = try {
        val tracks = when (kind) {
            ChartKind.COUNTRY -> {
                val countryTracks = countryChartTracks(context, country, limit)
                if (countryTracks.isEmpty()) {
                    RetrofitClient.create(context).getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = limit)
                        .collection.mapNotNull { it.track }
                } else {
                    countryTracks
                }
            }
            ChartKind.TOP -> {
                val api = RetrofitClient.create(context)
                val resolved = runCatching { api.resolveUrl(chartPlaylistUrl(countryCode, genre)) }.getOrNull()
                val kindOf = resolved?.get("kind")?.asString
                if (resolved != null && kindOf == "playlist") {
                    val playlist = Gson().fromJson(resolved, Playlist::class.java)
                    fillInMissingTracks(api, playlist.tracks.orEmpty()).take(limit)
                } else {
                    emptyList()
                }
            }
            ChartKind.TRENDING -> {
                RetrofitClient.create(context).getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = limit)
                    .collection.mapNotNull { it.track }
            }
        }
        tracks.filter { it.id > 0L }.distinctBy { it.id }.mapIndexed { i, t -> ChartEntry(i + 1, t) }
    } catch (_: Exception) {
        emptyList()
    }

    // ─── The country chart, and its cache ─────────────────────────────────────────────────────

    private val countryCache = ConcurrentHashMap<String, Pair<Long, List<Track>>>()
    private const val COUNTRY_CACHE_MS = 6 * 60 * 60 * 1000L

    /** Spotify's Top 50 playlist id per country, harvested from any search that surfaces them. */
    private val spotifyTop50 = ConcurrentHashMap<String, String>()
    private val spotifyTop50Searched = HashSet<String>()
    private val spotifyTop50Mutex = Mutex()

    private val gson = AppUtils.gson

    private data class CachedChart(
        @SerializedName("saved_at", alternate = ["savedAt"]) val savedAt: Long,
        @SerializedName("tracks") @JsonAdapter(SafeTrackListAdapter::class) val tracks: List<Track>
    )

    private fun chartCacheFile(context: Context, key: String): File =
        File(File(context.cacheDir, "country_charts"), key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    private suspend fun countryChartTracks(context: Context, country: ChartCountry?, limit: Int): List<Track> =
        // The chart's disk cache is read and written here: on the main thread it froze the charts
        // screen for the length of a JSON parse of fifty tracks on every open.
        withContext(Dispatchers.IO) {
            val key = "${country?.deezerName ?: "all"}/$limit"
            countryCache[key]?.takeIf { System.currentTimeMillis() - it.first < COUNTRY_CACHE_MS }
                ?.let { return@withContext it.second }
            readChartFromDisk(context, key)?.let {
                countryCache[key] = it
                return@withContext it.second
            }
            val tracks = loadCountryChart(context, country, limit)
            if (tracks.isNotEmpty()) {
                val stamped = System.currentTimeMillis() to tracks
                countryCache[key] = stamped
                runCatching {
                    val file = chartCacheFile(context, key)
                    file.parentFile?.mkdirs()
                    file.writeText(gson.toJson(CachedChart(stamped.first, tracks)))
                }
            }
            tracks
        }

    private fun readChartFromDisk(context: Context, key: String): Pair<Long, List<Track>>? = runCatching {
        val file = chartCacheFile(context, key)
        if (!file.exists()) return null
        val cached = gson.fromJson(file.readText(), CachedChart::class.java) ?: return null
        if (System.currentTimeMillis() - cached.savedAt > COUNTRY_CACHE_MS) return null
        val safeTracks = (cached.tracks as? List<*>)?.filterIsInstance<Track>() ?: emptyList()
        System.currentTimeMillis() to safeTracks
    }.getOrNull()

    /** Spotify first — the songs play as they are; Deezer's chart playlist second. */
    private suspend fun loadCountryChart(context: Context, country: ChartCountry?, limit: Int): List<Track> {
        if (country != null) {
            val spotify = spotifyCountryChart(country, limit)
            if (spotify.size >= minOf(limit, MIN_REAL_CHART)) return spotify
            val deezer = deezerCountryChart(country, limit)
            if (deezer.size >= minOf(limit, MIN_REAL_CHART)) return deezer
        }
        return emptyList()
    }

    private suspend fun spotifyCountryChart(country: ChartCountry, limit: Int): List<Track> = runCatching {
        val names = listOf(country.deezerName, spotifyCountryAlias(country)).distinct()
        val id = spotifyTop50Id(names) ?: return@runCatching emptyList()
        val playlist = SpotifyRepository.getPlaylist(id, maxTracks = limit) ?: return@runCatching emptyList()
        playlist.tracks.filter { it.isPlayable }.map { it.toTrack() }.take(limit)
    }.getOrDefault(emptyList())

    /** Spotify titles the US chart "Top 50 - USA". */
    private fun spotifyCountryAlias(country: ChartCountry) = when (country) {
        ChartCountry.US -> "USA"
        else -> country.deezerName
    }

    private suspend fun spotifyTop50Id(names: List<String>): String? = spotifyTop50Mutex.withLock {
        for (name in names) spotifyTop50[name.lowercase()]?.let { return it }
        for (name in names) {
            if (!synchronized(spotifyTop50Searched) { spotifyTop50Searched.add(name.lowercase()) }) continue
            val found = (0 until TOP50_PAGES).flatMap { page ->
                runCatching {
                    SpotifyRepository.search("Top 50 $name", limit = TOP50_PAGE_SIZE, offset = page * TOP50_PAGE_SIZE)
                        .playlists
                }.getOrDefault(emptyList())
            }
            found.filter { it.ownerName.equals("Spotify", ignoreCase = true) && it.name.startsWith("Top 50 - ", ignoreCase = true) }
                .forEach {
                    spotifyTop50[it.name.removePrefix("Top 50 - ").trim().lowercase()] = it.id
                }
            for (candidate in names) spotifyTop50[candidate.lowercase()]?.let { return it }
        }
        null
    }

    /** Deezer keeps a chart playlist per country, under "Top <country>". */
    private suspend fun deezerCountryChart(country: ChartCountry, limit: Int): List<Track> = runCatching {
        val name = country.deezerName
        val search = DeezerSearchRepository.search("Top $name", limit = 15)
        val top = search.playlists.firstOrNull {
            it.title.equals("Top $name", ignoreCase = true) && it.user?.username?.contains("Deezer", ignoreCase = true) == true
        } ?: return@runCatching emptyList()
        val playlist = DeezerSearchRepository.getPlaylist(top.permalink ?: return@runCatching emptyList())
        playlist?.tracks.orEmpty().filter { it.id > 0L && !it.title.isNullOrBlank() }.take(limit)
    }.getOrDefault(emptyList())

    // ─── Hydration ───────────────────────────────────────────────────────────────────────────

    /**
     * A resolved playlist embeds only its first few tracks in full; the rest are bare ids, which read as
     * "unknown" rows. The missing ones are fetched in batches and put back in the playlist's order.
     */
    private suspend fun fillInMissingTracks(api: com.alananasss.kittytune.data.network.SoundCloudApi, listed: List<Track>): List<Track> {
        val missing = listed.filter { it.title.isNullOrBlank() }.mapNotNull { it.id.takeIf { id -> id > 0L } }
        if (missing.isEmpty()) return listed
        val fetched = coroutineScope {
            missing.chunked(ID_BATCH).map { ids ->
                async(Dispatchers.IO) {
                    runCatching { api.getTracksByIds(ids.joinToString(",")) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.associateBy { it.id }
        return listed.mapNotNull { entry ->
            val track = if (entry.title.isNullOrBlank()) fetched[entry.id] else entry
            track?.takeIf { !it.title.isNullOrBlank() }
        }
    }

    /** The device's country, from the carrier, the SIM, or the locale, in that order. */
    object DeviceRegion {
        fun code(context: Context): String? {
            val telephony = runCatching {
                context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            }.getOrNull()
            val network = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
            val sim = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
            return (network ?: sim ?: Locale.getDefault().country)
                ?.takeIf { it.length == 2 }?.uppercase()
        }
    }

    private const val TOP50_PAGES = 3
    private const val TOP50_PAGE_SIZE = 30
    private const val MIN_REAL_CHART = 10
    private const val ID_BATCH = 50
}
