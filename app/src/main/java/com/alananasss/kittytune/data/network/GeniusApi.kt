package com.alananasss.kittytune.data.network

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Genius as a last-resort lyrics provider (issue #33).
 *
 * The desktop's copy of this file. Kept identical on purpose: the two apps should fall back to the same pages
 * and tidy the text the same way, or the same track shows different lyrics depending on which device you are
 * holding.
 *
 * Genius never has timings, so this can only ever return a plain block of text — the point is
 * coverage: unreleased tracks, live versions, SoundCloud exclusives and everything else that
 * LrcLib and Musixmatch have never heard of usually do have a Genius page.
 *
 * This talks to the same host the official Android app talks to, with that app's own logged-out
 * client token, which is why it needs no account and no API registration. `songs/{id}` with
 * `text_format=plain` returns the lyrics inline, so there is no HTML page to scrape and nothing
 * that breaks the next time the website is redesigned.
 */
object GeniusClient {

    private const val BASE_URL = "https://api.genius.com/"

    /**
     * The public logged-out token the Genius Android client ships with. It authenticates the
     * app, not a user: no account is involved and nothing about the listener is sent.
     */
    private const val LOGGED_OUT_TOKEN =
        "ZTejoT_ojOEasIkT9WrMBhBQOz6eYKK5QULCMECmOhvwqjRZ6WbpamFe3geHnvp3"

    private const val CLIENT_VERSION = "8.1.5"

    private val baseHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $LOGGED_OUT_TOKEN")
                    .header("X-Genius-Logged-Out", "true")
                    .header("X-Genius-Android-Version", CLIENT_VERSION)
                    .header("User-Agent", "Genius/$CLIENT_VERSION (Android; Android 13)")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val okHttpClient: okhttp3.OkHttpClient
        get() = ProxyManager.configureOkHttpClient(baseHttpClient.newBuilder()).build()

    private val api: GeniusApiService
        get() = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeniusApiService::class.java)

    /**
     * Song hits for [query], instrumentals and pages with no lyrics yet already dropped — those
     * would otherwise look like results and then resolve to nothing.
     */
    suspend fun search(query: String): List<GeniusSongHit> = withContext(Dispatchers.IO) {
        runCatching {
            api.searchSongs(query).response?.sections
                ?.filter { it.type == null || it.type == "song" }
                ?.flatMap { it.hits.orEmpty() }
                ?.mapNotNull { it.result }
                ?.filter { !it.instrumental && it.lyricsState != "unreleased" }
                ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /** The lyrics of one song as plain text, or null when Genius has none. */
    suspend fun lyrics(songId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            api.song(songId).response?.song?.lyrics?.plain?.let(::tidy)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Genius indents every line by one space and pads sections with runs of blank lines. Left
     * alone that shows up as a ragged left edge and gaping holes in the lyrics view.
     */
    private fun tidy(raw: String): String =
        raw.lineSequence()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}

interface GeniusApiService {
    @GET("search/songs")
    suspend fun searchSongs(@Query("q") query: String): GeniusSearchResponse

    @GET("songs/{id}")
    suspend fun song(
        @Path("id") id: Long,
        @Query("text_format") textFormat: String = "plain",
    ): GeniusSongResponse
}

data class GeniusSearchResponse(val response: GeniusSearchBody?)
data class GeniusSearchBody(val sections: List<GeniusSearchSection>?)
data class GeniusSearchSection(val type: String?, val hits: List<GeniusSearchHit>?)
data class GeniusSearchHit(val result: GeniusSongHit?)

data class GeniusSongHit(
    val id: Long,
    val title: String?,
    @SerializedName("artist_names") val artistNames: String?,
    @SerializedName("primary_artist_names") val primaryArtistNames: String?,
    @SerializedName("lyrics_state") val lyricsState: String?,
    val instrumental: Boolean = false,
    @SerializedName("release_date_for_display") val releaseDate: String?,
) {
    /** The credit to compare against the track's artist: primary name when Genius splits them. */
    val artist: String get() = primaryArtistNames ?: artistNames ?: ""
}

data class GeniusSongResponse(val response: GeniusSongBody?)
data class GeniusSongBody(val song: GeniusSongDetail?)
data class GeniusSongDetail(val id: Long, val title: String?, val lyrics: GeniusLyrics?)
data class GeniusLyrics(val plain: String?)
