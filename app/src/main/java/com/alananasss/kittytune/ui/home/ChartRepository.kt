package com.alananasss.kittytune.ui.home

import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches a ranked song list.
 *
 * Each kind is a different request, and every answer is ordered by the server, so the rank
 * is always the position and never the score:
 *
 * - [ChartKind.COUNTRY] is Spotify's own "Top 50 - <country>" where it keeps one, found in search by name
 *   and owner rather than from a table of ids that would go stale; where Spotify keeps none, Deezer's chart
 *   for the country, its tracks played through the configured audio sources like any Deezer track. Where
 *   neither is reachable the trending feed, rather than an empty list. See [countryChartTracks].
 * - [ChartKind.TRENDING] is `GET /charts?kind=trending`, the one live chart the endpoint serves. It
 *   ignores `genre` — every other genre than `all-music` comes back as an empty object — so the
 *   caller is not offered one here.
 * - [ChartKind.TOP] is the hand-curated 50 SoundCloud publishes per country and genre under
 *   `soundcloud.com/music-charts-<cc>/sets/<slug>`.
 *
 * @return the chart, or an empty list if it could not be read. A chart that failed is not a chart
 *   with fifty gaps in it, so nothing is invented to fill the row.
 */
internal suspend fun fetchChart(
    api: SoundCloudApi,
    kind: ChartKind,
    genre: ChartGenre,
    limit: Int,
    countryCode: String,
    country: ChartCountry? = null,
): List<ChartEntry> = try {
    val tracks: List<Track> = when (kind) {
        ChartKind.COUNTRY -> countryChartTracks(api, country, limit)

        ChartKind.TRENDING -> api.getCharts(
            kind = "trending",
            genre = "soundcloud:genres:all-music",
            limit = limit,
        ).collection.mapNotNull { it.track }

        ChartKind.TOP -> {
            val url = ChartsViewModel.chartPlaylistUrl(countryCode, genre)
            val resolved = api.resolveUrl(url)
            // The chart playlists are the only place a `kind` other than trending comes from, and
            // they are ordinary playlists: a resolve that answers with something else is a slug that
            // has moved, not a chart.
            if (resolved.get("kind")?.asString == "playlist") {
                val listed = Gson().fromJson(resolved, Playlist::class.java)?.tracks.orEmpty()
                fillInMissingTracks(api, listed.take(limit))
            } else {
                emptyList()
            }
        }
    }
    tracks
        .filter { it.id > 0L }
        .distinctBy { it.id }
        .mapIndexed { index, track -> ChartEntry(rank = index + 1, track = track, score = 0.0) }
} catch (e: Exception) {
    e.printStackTrace()
    emptyList()
}

/**
 * The chart of [country]: Spotify's own "Top 50" where it keeps one, else Deezer's where it keeps a real one,
 * else the trending feed rather than nothing. A country with no chart of its own anywhere reachable is shown
 * the trending feed instead of someone else's local chart (issue #66).
 */
private suspend fun countryChartTracks(api: SoundCloudApi, country: ChartCountry?, limit: Int): List<Track> {
    val key = "${country?.deezerName ?: "all"}/$limit"
    // A chart changes over days, not minutes: the second visit to a country answers at once.
    countryCache[key]?.takeIf { System.currentTimeMillis() - it.first < COUNTRY_CACHE_MS }?.let { return it.second }
    // Kept on disk too, so a country whose chart opens slowly answers at once on the next launch.
    readChartFromDisk(key)?.let { countryCache[key] = it; return it.second }
    val tracks = loadCountryChart(api, country, limit)
    if (tracks.isNotEmpty()) {
        val now = System.currentTimeMillis()
        countryCache[key] = now to tracks
        writeChartToDisk(key, now, tracks)
    }
    return tracks
}

private class CachedChart(val savedAt: Long, val tracks: List<Track>)

private fun chartCacheFile(key: String): File =
    File(KittyTuneApp.instance.cacheDir, "country_charts/" + key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

private fun readChartFromDisk(key: String): Pair<Long, List<Track>>? = runCatching {
    val file = chartCacheFile(key)
    if (!file.exists()) return null
    val cached = Gson().fromJson(file.readText(), CachedChart::class.java) ?: return null
    if (System.currentTimeMillis() - cached.savedAt >= COUNTRY_CACHE_MS || cached.tracks.isEmpty()) return null
    cached.savedAt to cached.tracks
}.getOrNull()

private fun writeChartToDisk(key: String, savedAt: Long, tracks: List<Track>) {
    runCatching {
        val file = chartCacheFile(key)
        file.parentFile?.mkdirs()
        file.writeText(Gson().toJson(CachedChart(savedAt, tracks)))
    }
}

private val countryCache = ConcurrentHashMap<String, Pair<Long, List<Track>>>()

/** How long a country's chart is kept before it is read again. */
private const val COUNTRY_CACHE_MS = 6 * 60 * 60 * 1000L

private suspend fun loadCountryChart(api: SoundCloudApi, country: ChartCountry?, limit: Int): List<Track> {
    // Spotify's own "Top 50" for the country first: its songs play as they are, so nothing has to be matched
    // and the chart opens in a second or two instead of waiting on fifty searches.
    if (country != null) {
        val spotify = spotifyCountryChart(country, limit)
        if (spotify.size >= minOf(limit, MIN_REAL_CHART)) return spotify
    }
    if (country == null || country.hasDeezerChart) {
        val deezer = com.alananasss.kittytune.data.deezer.DeezerSearchRepository.countryChart(limit, country?.deezerName)
        if (deezer.size >= minOf(limit, MIN_REAL_CHART) || country == null) return deezer
    }
    // Neither chart reachable: the trending feed, rather than an empty list. The desktop matches Apple Music's
    // chart song by song here; the Android app keeps no Apple Music client, so the listener still gets a live
    // chart instead of someone else's local one.
    return runCatching {
        api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = limit)
            .collection.mapNotNull { it.track }
    }.getOrDefault(emptyList())
}

/**
 * Spotify's own "Top 50 - <country>" playlist for [country], or nothing where Spotify keeps none (not Russia, not
 * Vietnam). The playlists are found in Spotify's search by their name and by Spotify being their owner, once per session,
 * rather than from a table of ids that would go stale.
 */
private suspend fun spotifyCountryChart(country: ChartCountry, limit: Int): List<Track> = runCatching {
    val id = spotifyTop50Id(listOf(country.deezerName, spotifyCountryAlias(country)).distinct())
        ?: return@runCatching emptyList()
    com.alananasss.kittytune.data.spotify.SpotifyRepository.getPlaylist(id, maxTracks = limit)
        ?.tracks.orEmpty().filter { it.isPlayable }.map { it.toTrack() }.take(limit)
}.getOrDefault(emptyList())

/** How Spotify titles the playlist where it differs from the name Deezer uses. */
private fun spotifyCountryAlias(country: ChartCountry): String = when (country) {
    ChartCountry.US -> "USA"
    else -> country.deezerName
}

private val spotifyTop50Mutex = Mutex()

/** Lower-case country name to the id of Spotify's "Top 50 - <country>", filled in as countries are asked for. */
private val spotifyTop50 = ConcurrentHashMap<String, String>()

/** Countries searched for already, so one that Spotify has no chart for is not searched for again and again. */
private val spotifyTop50Searched = ConcurrentHashMap.newKeySet<String>()

/**
 * The id of Spotify's own "Top 50 - <country>" for the first of [names] that has one. Each name is looked up in Spotify's
 * search by "Top 50 <name>" (the dash is left out: with it Spotify's search buried its own playlists under other people's),
 * and only a playlist owned by Spotify with exactly that title is taken. One search turns up the charts of many countries
 * at once, so they are all remembered.
 */
private suspend fun spotifyTop50Id(names: List<String>): String? = spotifyTop50Mutex.withLock {
    for (name in names) {
        spotifyTop50[name.lowercase()]?.let { return it }
    }
    for (name in names) {
        if (!spotifyTop50Searched.add(name.lowercase())) continue
        val found = (0 until TOP50_PAGES).flatMap { page ->
            com.alananasss.kittytune.data.spotify.SpotifyRepository
                .search("Top 50 $name", limit = TOP50_PAGE_SIZE, offset = page * TOP50_PAGE_SIZE).playlists
        }
        found.filter { it.ownerName.equals("Spotify", ignoreCase = true) && it.name.startsWith("Top 50 - ", ignoreCase = true) }
            .forEach { spotifyTop50[it.name.removePrefix("Top 50 - ").trim().lowercase()] = it.id }
        for (candidate in names) {
            spotifyTop50[candidate.lowercase()]?.let { return it }
        }
    }
    null
}

private const val TOP50_PAGES = 3
private const val TOP50_PAGE_SIZE = 30

/** Fewer songs than this from Deezer is a chart it no longer keeps up. */
private const val MIN_REAL_CHART = 10

/**
 * Fills in the tracks a resolved playlist only names.
 *
 * `/resolve` embeds the first handful of a playlist's tracks in full and hands back the rest as a
 * bare id — no title, no artist, no artwork, and a null play count that the model turns into a zero.
 * A chart built straight off that is five real songs followed by forty-five rows reading "unknown
 * title, unknown artist, 0 plays", which is what the screen showed.
 *
 * So the nameless ones are asked for by id, in batches, and put back where the playlist had them:
 * `/tracks?ids=` answers in whatever order it likes, and a chart's whole meaning is its order.
 *
 * A track that is still nameless afterwards has been deleted, blocked or made private, and is
 * dropped rather than shown as a gap — a chart with a hole in it is honest; one with a row of
 * "unknown" is a bug wearing a chart's clothes.
 */
private suspend fun fillInMissingTracks(
    api: SoundCloudApi,
    listed: List<Track>,
): List<Track> {
    val missing = listed
        .filter { it.title.isNullOrBlank() }
        .mapNotNull { it.id.takeIf { id -> id > 0L } }
    if (missing.isEmpty()) return listed.filter { !it.title.isNullOrBlank() }

    val fetched = missing.chunked(ID_BATCH).map { batch ->
        runCatching { api.getTracksByIds(batch.joinToString(",")) }.getOrDefault(emptyList())
    }.flatten().associateBy { it.id }

    return listed.mapNotNull { entry ->
        val track = if (entry.title.isNullOrBlank()) fetched[entry.id] else entry
        track?.takeIf { !it.title.isNullOrBlank() }
    }
}

/** How many ids to ask for at once. The same batch size the new-releases screen uses. */
private const val ID_BATCH = 50
