package com.alananasss.kittytune.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Media
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.utils.AppUtils
import com.alananasss.kittytune.utils.LocaleUtils
import com.google.gson.JsonObject
import java.util.Locale

enum class CategoryKind {
    GENRE,
    MOOD
}

data class ParsedCategoryDetails(
    val title: String? = null,
    val headerDarkArtwork: String? = null,
    val headerLightArtwork: String? = null,
    val tracks: List<Track> = emptyList(),
    val vibeTracks: List<Track> = emptyList(),
    val curatedPlaylists: List<Playlist> = emptyList(),
    val communityPlaylists: List<Playlist> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val artists: List<User> = emptyList(),
    val introducingPlaylist: Playlist? = null,
    val introducingTracks: List<Track> = emptyList(),
    val discoverMoreTracks: List<Track> = emptyList(),
    val filterChoices: List<String> = emptyList()
)

data class SearchCategory(
    val id: String,
    val title: String,
    val query: String,
    val icon: ImageVector = Icons.Rounded.MusicNote,
    val titleRes: Int = 0,
    val artworkUrl: String? = null,
    val artworkUrlLight: String? = null,
    val borderColor: String? = null,
    val systemPlaylistUrn: String? = null,
    val kind: CategoryKind = CategoryKind.GENRE
)

object GenreData {

    private fun getString(context: Context, resId: Int): String {
        val str: String? = try {
            LocaleUtils.updateBaseContextLocale(context).getString(resId)
        } catch (_: Throwable) {
            try {
                context.getString(resId)
            } catch (_: Throwable) {
                null
            }
        }
        return str ?: "Category_$resId"
    }

    fun parseBorderColor(colorName: String?): Color {
        return when (colorName?.lowercase(Locale.ROOT)) {
            "violet" -> Color(0xFF7C4DFF)
            "magenta" -> Color(0xFFE91E63)
            "yellow" -> Color(0xFFFFB300)
            "teal" -> Color(0xFF009688)
            "orange" -> Color(0xFFFF6F00)
            "green" -> Color(0xFF43A047)
            "blue" -> Color(0xFF1E88E5)
            "red" -> Color(0xFFE53935)
            "purple" -> Color(0xFF8E24AA)
            else -> Color(0xFF757575)
        }
    }

    /**
     * Official 21 SoundCloud APK Visual Grid categories extracted from
     * `assets/query_landing_page.json` (soundcloud:sections:search_genre_grid)
     * plus Country, matching official layouts and artwork.
     */
    fun getVisualCategories(context: Context): List<SearchCategory> {
        return listOf(
            SearchCategory(
                id = "hiphop",
                title = getString(context, R.string.category_genre_hiphop),
                query = "Hip Hop",
                icon = Icons.Rounded.Mic,
                titleRes = R.string.category_genre_hiphop,
                artworkUrl = "https://i1.sndcdn.com/artworks-pR3Ub8FmtdtYbjUL-umUIAw-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-jxXE4PgVi9t4Oqg0-Nf877w-original.png",
                borderColor = "violet",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:hip-hop",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "electronic",
                title = getString(context, R.string.category_genre_electro),
                query = "Electronic",
                icon = Icons.Rounded.FlashOn,
                titleRes = R.string.category_genre_electro,
                artworkUrl = "https://i1.sndcdn.com/artworks-yHoTvedxjvZx1ZT7-zoy2DA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-ZIIZeluYugG7mlDe-HYzYrg-original.png",
                borderColor = "magenta",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:electronic",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "pop",
                title = getString(context, R.string.category_genre_pop),
                query = "Pop",
                icon = Icons.Rounded.Star,
                titleRes = R.string.category_genre_pop,
                artworkUrl = "https://i1.sndcdn.com/artworks-SEELdwrEvZ5IDlth-VxxTiQ-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-lD84DECwOsKxBghz-VyWeog-original.png",
                borderColor = "yellow",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:pop",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "rnb",
                title = getString(context, R.string.category_genre_rnb),
                query = "R&B",
                icon = Icons.Rounded.FavoriteBorder,
                titleRes = R.string.category_genre_rnb,
                artworkUrl = "https://i1.sndcdn.com/artworks-m9QYuBMGzdfXu7YY-8WJtoA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-1oXx7z3rIP365z4H-0kzoWg-original.png",
                borderColor = "teal",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:r-n-b",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "chill",
                title = getString(context, R.string.category_mood_chill),
                query = "Chill",
                icon = Icons.Rounded.Spa,
                titleRes = R.string.category_mood_chill,
                artworkUrl = "https://i1.sndcdn.com/artworks-oybL0Q76O6n3UAxs-92JAGw-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-U2zrZvozo8Uzj11W-XyKymQ-original.png",
                borderColor = "teal",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:ambient",
                kind = CategoryKind.MOOD
            ),
            SearchCategory(
                id = "party",
                title = getString(context, R.string.category_mood_party),
                query = "Party",
                icon = Icons.Rounded.Celebration,
                titleRes = R.string.category_mood_party,
                artworkUrl = "https://i1.sndcdn.com/artworks-lyVFV7CTzSMzQbzX-UsSXOw-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-a6kRIUlyz5Yyzbxq-uSBxFg-original.png",
                borderColor = "orange",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:dancehall",
                kind = CategoryKind.MOOD
            ),
            SearchCategory(
                id = "workout",
                title = getString(context, R.string.category_mood_workout),
                query = "Workout",
                icon = Icons.Rounded.FitnessCenter,
                titleRes = R.string.category_mood_workout,
                artworkUrl = "https://i1.sndcdn.com/artworks-StN4aSfwmhJoogbN-fKAxbg-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-YPunq6QGHybGiFSe-8CDbzA-original.png",
                borderColor = "green",
                kind = CategoryKind.MOOD
            ),
            SearchCategory(
                id = "techno",
                title = getString(context, R.string.category_genre_techno),
                query = "Techno",
                icon = Icons.Rounded.GraphicEq,
                titleRes = R.string.category_genre_techno,
                artworkUrl = "https://i1.sndcdn.com/artworks-XzsP0wECFkyixq91-8qXnwA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-pB9ouQgZHGmcRzas-rOgVQg-original.png",
                borderColor = "magenta",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:electronic",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "house",
                title = getString(context, R.string.category_genre_house),
                query = "House",
                icon = Icons.Rounded.Nightlife,
                titleRes = R.string.category_genre_house,
                artworkUrl = "https://i1.sndcdn.com/artworks-u9qdrklZrNcwYrTz-W0HEIQ-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-zNGgVHhD2LHBeXQY-IzKeAQ-original.png",
                borderColor = "magenta",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:house",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "happy",
                title = getString(context, R.string.category_mood_feelgood),
                query = "Feel Good",
                icon = Icons.Rounded.Mood,
                titleRes = R.string.category_mood_feelgood,
                artworkUrl = "https://i1.sndcdn.com/artworks-mM4AS15OVqxwHUTL-HfJ2MA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-lq9ZG8CP1JwaNadI-exJszg-original.png",
                borderColor = "yellow",
                kind = CategoryKind.MOOD
            ),
            SearchCategory(
                id = "athome",
                title = getString(context, R.string.category_mood_athome),
                query = "At Home",
                icon = Icons.Rounded.Home,
                titleRes = R.string.category_mood_athome,
                artworkUrl = "https://i1.sndcdn.com/artworks-4ACTXI6c1RIhhbuJ-zRNZaA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-IATddv4HgI2P4RDf-TXi2Ow-original.png",
                borderColor = "violet",
                kind = CategoryKind.MOOD
            ),
            SearchCategory(
                id = "sad",
                title = getString(context, R.string.category_mood_sad),
                query = "Sad",
                icon = Icons.Rounded.SentimentVeryDissatisfied,
                titleRes = R.string.category_mood_sad,
                artworkUrl = "https://i1.sndcdn.com/artworks-kfBDERNs1Ucjxt4j-SazrsA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-KgSgbgr6l7JjueWT-v04KyA-original.png",
                borderColor = "blue",
                kind = CategoryKind.MOOD
            ),
            SearchCategory(
                id = "study",
                title = getString(context, R.string.category_mood_study),
                query = "Study",
                icon = Icons.Rounded.Psychology,
                titleRes = R.string.category_mood_study,
                artworkUrl = "https://i1.sndcdn.com/artworks-Bo1d3QcPqib2Bhif-n4A2Nw-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-o2CX47CyhmLrwmFY-FAhgbA-original.png",
                borderColor = "magenta",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:ambient",
                kind = CategoryKind.MOOD
            ),
            SearchCategory(
                id = "indie",
                title = "Indie",
                query = "Indie",
                icon = Icons.Rounded.Album,
                artworkUrl = "https://i1.sndcdn.com/artworks-a09JHvZH0yjKNydz-VQotYA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-BaiTFPs9F7VzRroT-qLYKvQ-original.png",
                borderColor = "blue",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:indie",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "rock",
                title = getString(context, R.string.category_genre_rock),
                query = "Rock",
                icon = Icons.Rounded.Whatshot,
                titleRes = R.string.category_genre_rock,
                artworkUrl = "https://i1.sndcdn.com/artworks-m7XoTLjQDHyAP4iQ-mDLL6w-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-1ziSYABS5ZSXkGeH-brmK1Q-original.png",
                borderColor = "red",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:rock",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "folk",
                title = getString(context, R.string.category_genre_folk),
                query = "Folk",
                icon = Icons.Rounded.Forest,
                titleRes = R.string.category_genre_folk,
                artworkUrl = "https://i1.sndcdn.com/artworks-IujP8FmENKj2GmfN-BGpzoQ-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-2wzLSZ6D3HMYR0T0-7DRJcg-original.png",
                borderColor = "orange",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:folk",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "metal",
                title = getString(context, R.string.category_genre_metal),
                query = "Metal",
                icon = Icons.Rounded.Bolt,
                titleRes = R.string.category_genre_metal,
                artworkUrl = "https://i1.sndcdn.com/artworks-DqCcOG0lSUsInrH8-Q72W8w-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-odMAHfTyAhtrvhEn-p9dy6Q-original.png",
                borderColor = "red",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:metal",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "latin",
                title = getString(context, R.string.category_genre_latin),
                query = "Latin",
                icon = Icons.Rounded.Public,
                titleRes = R.string.category_genre_latin,
                artworkUrl = "https://i1.sndcdn.com/artworks-0OpfXvzMD5N5y3vX-BBEzKA-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-RPS1BZ7HRzZ5Y5yo-2B9lnQ-original.png",
                borderColor = "purple",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:latin",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "reggae",
                title = getString(context, R.string.category_genre_reggae),
                query = "Reggae",
                icon = Icons.Rounded.Public,
                titleRes = R.string.category_genre_reggae,
                artworkUrl = "https://i1.sndcdn.com/artworks-JNbjJPkX8yBTSqni-NZUUpQ-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-aoMb23v7d2DZGpHs-f26zCg-original.png",
                borderColor = "green",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:reggae",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "soul",
                title = getString(context, R.string.category_genre_soul),
                query = "Soul",
                icon = Icons.Rounded.Favorite,
                titleRes = R.string.category_genre_soul,
                artworkUrl = "https://i1.sndcdn.com/artworks-RcVdTJiX4pzxuRkC-wPSiag-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-5eGLPJSEyou5RzIx-zWKF0Q-original.png",
                borderColor = "teal",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:r-n-b",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "country",
                title = getString(context, R.string.category_genre_country),
                query = "Country",
                icon = Icons.Rounded.MusicNote,
                titleRes = R.string.category_genre_country,
                artworkUrl = "https://i1.sndcdn.com/artworks-2wzLSZ6D3HMYR0T0-7DRJcg-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-IujP8FmENKj2GmfN-BGpzoQ-original.png",
                borderColor = "orange",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:country",
                kind = CategoryKind.GENRE
            ),
            SearchCategory(
                id = "disco",
                title = getString(context, R.string.category_genre_disco),
                query = "Disco",
                icon = Icons.Rounded.Album,
                titleRes = R.string.category_genre_disco,
                artworkUrl = "https://i1.sndcdn.com/artworks-ysDIjIMVNzGqxQlo-M9kq2g-original.png",
                artworkUrlLight = "https://i1.sndcdn.com/artworks-KBXvLcTDLy2JABbg-HzYLrA-original.png",
                borderColor = "yellow",
                systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:dancehall",
                kind = CategoryKind.GENRE
            )
        )
    }

    fun getMoods(context: Context): List<SearchCategory> {
        return listOf(
            SearchCategory("feelgood", getString(context, R.string.category_mood_feelgood), "Feel Good", Icons.Rounded.Mood, R.string.category_mood_feelgood,
                artworkUrl = "https://i1.sndcdn.com/artworks-mM4AS15OVqxwHUTL-HfJ2MA-original.png", borderColor = "yellow", kind = CategoryKind.MOOD),
            SearchCategory("calm", getString(context, R.string.category_mood_calm), "Calm Relax", Icons.Rounded.Spa, R.string.category_mood_calm,
                artworkUrl = "https://i1.sndcdn.com/artworks-oybL0Q76O6n3UAxs-92JAGw-original.png", borderColor = "teal", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:ambient", kind = CategoryKind.MOOD),
            SearchCategory("focus", getString(context, R.string.category_mood_focus), "Focus Study", Icons.Rounded.Psychology, R.string.category_mood_focus,
                artworkUrl = "https://i1.sndcdn.com/artworks-Bo1d3QcPqib2Bhif-n4A2Nw-original.png", borderColor = "magenta", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:ambient", kind = CategoryKind.MOOD),
            SearchCategory("energy", getString(context, R.string.category_mood_energy), "Energy", Icons.Rounded.LocalFireDepartment, R.string.category_mood_energy,
                borderColor = "orange", kind = CategoryKind.MOOD),
            SearchCategory("workout", getString(context, R.string.category_mood_workout), "Workout", Icons.Rounded.FitnessCenter, R.string.category_mood_workout,
                artworkUrl = "https://i1.sndcdn.com/artworks-StN4aSfwmhJoogbN-fKAxbg-original.png", borderColor = "green", kind = CategoryKind.MOOD),
            SearchCategory("gaming", getString(context, R.string.category_mood_gaming), "Gaming", Icons.Rounded.SportsEsports, R.string.category_mood_gaming,
                borderColor = "violet", kind = CategoryKind.MOOD),
            SearchCategory("winter", getString(context, R.string.category_mood_winter), "Winter", Icons.Rounded.AcUnit, R.string.category_mood_winter,
                borderColor = "blue", kind = CategoryKind.MOOD),
            SearchCategory("driving", getString(context, R.string.category_mood_driving), "Driving", Icons.Rounded.DirectionsCar, R.string.category_mood_driving,
                borderColor = "orange", kind = CategoryKind.MOOD),
            SearchCategory("romance", getString(context, R.string.category_mood_romance), "Romantic", Icons.Rounded.Favorite, R.string.category_mood_romance,
                borderColor = "red", kind = CategoryKind.MOOD),
            SearchCategory("party", getString(context, R.string.category_mood_party), "Party", Icons.Rounded.Celebration, R.string.category_mood_party,
                artworkUrl = "https://i1.sndcdn.com/artworks-lyVFV7CTzSMzQbzX-UsSXOw-original.png", borderColor = "orange", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:dancehall", kind = CategoryKind.MOOD),
            SearchCategory("sleep", getString(context, R.string.category_mood_sleep), "Sleep", Icons.Rounded.Bedtime, R.string.category_mood_sleep,
                borderColor = "blue", kind = CategoryKind.MOOD),
            SearchCategory("sad", getString(context, R.string.category_mood_sad), "Sad", Icons.Rounded.SentimentVeryDissatisfied, R.string.category_mood_sad,
                artworkUrl = "https://i1.sndcdn.com/artworks-kfBDERNs1Ucjxt4j-SazrsA-original.png", borderColor = "blue", kind = CategoryKind.MOOD),
            SearchCategory("athome", getString(context, R.string.category_mood_athome), "At Home", Icons.Rounded.Home, R.string.category_mood_athome,
                artworkUrl = "https://i1.sndcdn.com/artworks-4ACTXI6c1RIhhbuJ-zRNZaA-original.png", borderColor = "violet", kind = CategoryKind.MOOD),
            SearchCategory("study", getString(context, R.string.category_mood_study), "Study", Icons.Rounded.Psychology, R.string.category_mood_study,
                artworkUrl = "https://i1.sndcdn.com/artworks-Bo1d3QcPqib2Bhif-n4A2Nw-original.png", borderColor = "magenta", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:ambient", kind = CategoryKind.MOOD),
            SearchCategory("chill", getString(context, R.string.category_mood_chill), "Chill", Icons.Rounded.Spa, R.string.category_mood_chill,
                artworkUrl = "https://i1.sndcdn.com/artworks-oybL0Q76O6n3UAxs-92JAGw-original.png", borderColor = "teal", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:ambient", kind = CategoryKind.MOOD)
        )
    }

    fun getGenres(context: Context): List<SearchCategory> {
        return listOf(
            SearchCategory("hiphop", getString(context, R.string.category_genre_hiphop), "Hip Hop", Icons.Rounded.Mic, R.string.category_genre_hiphop,
                artworkUrl = "https://i1.sndcdn.com/artworks-pR3Ub8FmtdtYbjUL-umUIAw-original.png", borderColor = "violet", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:hip-hop"),
            SearchCategory("rapfr", getString(context, R.string.category_genre_rapfr), "Rap FR", Icons.Rounded.Mic, R.string.category_genre_rapfr,
                borderColor = "violet", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:hip-hop"),
            SearchCategory("phonk", getString(context, R.string.category_genre_phonk_fix), "Phonk", Icons.Rounded.TimeToLeave, R.string.category_genre_phonk_fix,
                borderColor = "purple"),
            SearchCategory("rnb", getString(context, R.string.category_genre_rnb), "R&B", Icons.Rounded.FavoriteBorder, R.string.category_genre_rnb,
                artworkUrl = "https://i1.sndcdn.com/artworks-m9QYuBMGzdfXu7YY-8WJtoA-original.png", borderColor = "teal", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:r-n-b"),
            SearchCategory("funk", getString(context, R.string.category_genre_funk), "Funk", Icons.Rounded.Nightlife, R.string.category_genre_funk,
                borderColor = "yellow"),
            SearchCategory("disco", getString(context, R.string.category_genre_disco), "Disco", Icons.Rounded.Album, R.string.category_genre_disco,
                artworkUrl = "https://i1.sndcdn.com/artworks-ysDIjIMVNzGqxQlo-M9kq2g-original.png", borderColor = "yellow", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:dancehall"),

            SearchCategory("dance", getString(context, R.string.category_genre_dance), "EDM", Icons.Rounded.FlashOn, R.string.category_genre_dance,
                artworkUrl = "https://i1.sndcdn.com/artworks-yHoTvedxjvZx1ZT7-zoy2DA-original.png", borderColor = "magenta", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:electronic"),
            SearchCategory("house", getString(context, R.string.category_genre_house), "House", Icons.Rounded.Nightlife, R.string.category_genre_house,
                artworkUrl = "https://i1.sndcdn.com/artworks-u9qdrklZrNcwYrTz-W0HEIQ-original.png", borderColor = "magenta", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:house"),
            SearchCategory("techno", getString(context, R.string.category_genre_techno), "Techno", Icons.Rounded.GraphicEq, R.string.category_genre_techno,
                artworkUrl = "https://i1.sndcdn.com/artworks-XzsP0wECFkyixq91-8qXnwA-original.png", borderColor = "magenta", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:electronic"),
            SearchCategory("hardstyle", getString(context, R.string.category_genre_hardstyle), "Hardstyle", Icons.Rounded.Bolt, R.string.category_genre_hardstyle,
                borderColor = "orange"),
            SearchCategory("trance", getString(context, R.string.category_genre_trance), "Trance", Icons.Rounded.Waves, R.string.category_genre_trance,
                borderColor = "teal"),
            SearchCategory("dubstep", getString(context, R.string.category_genre_dubstep), "Dubstep", Icons.Rounded.GraphicEq, R.string.category_genre_dubstep,
                borderColor = "magenta"),
            SearchCategory("pluggnb", getString(context, R.string.category_genre_pluggnb), "Pluggnb", Icons.Rounded.Cloud, R.string.category_genre_pluggnb),
            SearchCategory("dreamcore", getString(context, R.string.category_genre_dreamcore), "Dreamcore", Icons.Rounded.AutoAwesome, R.string.category_genre_dreamcore),
            SearchCategory("glitchcore", getString(context, R.string.category_genre_glitchcore), "Glitchcore", Icons.Rounded.SdCardAlert, R.string.category_genre_glitchcore),
            SearchCategory("eurobeat", getString(context, R.string.category_genre_eurobeat), "Eurobeat", Icons.Rounded.Bolt, R.string.category_genre_eurobeat),
            SearchCategory("cloudrap", getString(context, R.string.category_genre_cloudrap), "Cloud Rap", Icons.Rounded.WbCloudy, R.string.category_genre_cloudrap),
            SearchCategory("drumandbass", getString(context, R.string.category_genre_drumandbass), "Drum and Bass", Icons.Rounded.FastForward, R.string.category_genre_drumandbass),
            SearchCategory("jungle", getString(context, R.string.category_genre_jungle), "Jungle", Icons.Rounded.Forest, R.string.category_genre_jungle),
            SearchCategory("breakcore", getString(context, R.string.category_genre_breakcore), "Breakcore", Icons.Rounded.BrokenImage, R.string.category_genre_breakcore),
            SearchCategory("garage", getString(context, R.string.category_genre_garage), "UK Garage", Icons.Rounded.Garage, R.string.category_genre_garage),
            SearchCategory("idm", getString(context, R.string.category_genre_idm), "IDM", Icons.Rounded.Memory, R.string.category_genre_idm),

            SearchCategory("pop", getString(context, R.string.category_genre_pop), "Pop", Icons.Rounded.Star, R.string.category_genre_pop,
                artworkUrl = "https://i1.sndcdn.com/artworks-SEELdwrEvZ5IDlth-VxxTiQ-original.png", borderColor = "yellow", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:pop"),
            SearchCategory("hyperpop", getString(context, R.string.category_genre_hyperpop), "Hyperpop", Icons.Rounded.Flare, R.string.category_genre_hyperpop),
            SearchCategory("scenecore", getString(context, R.string.category_genre_scenecore), "Scenecore", Icons.Rounded.Style, R.string.category_genre_scenecore),
            SearchCategory("digicore", getString(context, R.string.category_genre_digicore), "Digicore", Icons.Rounded.DataObject, R.string.category_genre_digicore),
            SearchCategory("vocaloid", getString(context, R.string.category_genre_vocaloid), "Vocaloid", Icons.Rounded.Face, R.string.category_genre_vocaloid),
            SearchCategory("lolicore", getString(context, R.string.category_genre_lolicore), "Lolicore", Icons.Rounded.ChildCare, R.string.category_genre_lolicore),
            SearchCategory("anime", getString(context, R.string.category_genre_anime), "Anime", Icons.Rounded.Animation, R.string.category_genre_anime),
            SearchCategory("weeb", getString(context, R.string.category_genre_weeb), "Otaku", Icons.Rounded.AutoAwesome, R.string.category_genre_weeb),
            SearchCategory("nightcore", getString(context, R.string.category_genre_nightcore), "Nightcore", Icons.Rounded.HistoryToggleOff, R.string.category_genre_nightcore),
            SearchCategory("bedroompop", getString(context, R.string.category_genre_bedroompop), "Bedroom Pop", Icons.Rounded.Bed, R.string.category_genre_bedroompop),
            SearchCategory("popjp", getString(context, R.string.category_genre_popjp), "J-Pop", Icons.Rounded.MusicNote, R.string.category_genre_popjp),
            SearchCategory("kpop", getString(context, R.string.category_genre_kpop), "K-Pop", Icons.Rounded.StarBorder, R.string.category_genre_kpop),
            SearchCategory("popfr", getString(context, R.string.category_genre_popfr), "Variété Française", Icons.Rounded.MusicNote, R.string.category_genre_popfr),
            SearchCategory("urbanfr", getString(context, R.string.category_genre_urbanfr), "Pop Urbaine", Icons.Rounded.Mic, R.string.category_genre_urbanfr),

            SearchCategory("rock", getString(context, R.string.category_genre_rock), "Rock", Icons.Rounded.Whatshot, R.string.category_genre_rock,
                artworkUrl = "https://i1.sndcdn.com/artworks-m7XoTLjQDHyAP4iQ-mDLL6w-original.png", borderColor = "red", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:rock"),
            SearchCategory("alt", getString(context, R.string.category_genre_alt), "Alternative", Icons.Rounded.Album, R.string.category_genre_alt,
                artworkUrl = "https://i1.sndcdn.com/artworks-a09JHvZH0yjKNydz-VQotYA-original.png", borderColor = "blue", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:indie"),
            SearchCategory("metal", getString(context, R.string.category_genre_metal), "Metal", Icons.Rounded.Bolt, R.string.category_genre_metal,
                artworkUrl = "https://i1.sndcdn.com/artworks-DqCcOG0lSUsInrH8-Q72W8w-original.png", borderColor = "red", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:metal"),
            SearchCategory("emo", getString(context, R.string.category_genre_emo), "Emo", Icons.Rounded.SentimentDissatisfied, R.string.category_genre_emo),
            SearchCategory("grunge", getString(context, R.string.category_genre_grunge), "Grunge", Icons.Rounded.MusicOff, R.string.category_genre_grunge),
            SearchCategory("shoegaze", getString(context, R.string.category_genre_shoegaze), "Shoegaze", Icons.Rounded.Waves, R.string.category_genre_shoegaze),

            SearchCategory("lofi", getString(context, R.string.category_genre_lofi), "Lofi", Icons.Rounded.LocalCafe, R.string.category_genre_lofi),
            SearchCategory("ambient", getString(context, R.string.category_genre_ambient), "Ambient", Icons.Rounded.WbCloudy, R.string.category_genre_ambient),
            SearchCategory("vaporwave", getString(context, R.string.category_genre_vaporwave), "Vaporwave", Icons.Rounded.Computer, R.string.category_genre_vaporwave),
            SearchCategory("synthwave", getString(context, R.string.category_genre_synthwave), "Synthwave", Icons.Rounded.Brightness4, R.string.category_genre_synthwave),

            SearchCategory("latin", getString(context, R.string.category_genre_latin), "Latin", Icons.Rounded.Public, R.string.category_genre_latin,
                artworkUrl = "https://i1.sndcdn.com/artworks-0OpfXvzMD5N5y3vX-BBEzKA-original.png", borderColor = "purple", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:latin"),
            SearchCategory("afro", getString(context, R.string.category_genre_afro), "Afrobeat", Icons.Rounded.Public, R.string.category_genre_afro),
            SearchCategory("reggae", getString(context, R.string.category_genre_reggae), "Reggae", Icons.Rounded.Public, R.string.category_genre_reggae,
                artworkUrl = "https://i1.sndcdn.com/artworks-JNbjJPkX8yBTSqni-NZUUpQ-original.png", borderColor = "green", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:reggae"),
            SearchCategory("arabic", getString(context, R.string.category_genre_arabic), "Arabic", Icons.Rounded.MusicNote, R.string.category_genre_arabic),
            SearchCategory("bollywood", getString(context, R.string.category_genre_bollywood), "Bollywood", Icons.Rounded.MusicNote, R.string.category_genre_bollywood),
            SearchCategory("brazil", getString(context, R.string.category_genre_brazil), "Brazil", Icons.Rounded.Public, R.string.category_genre_brazil),
            SearchCategory("afrocuban", getString(context, R.string.category_genre_afrocuban), "Afro-Cuban", Icons.Rounded.Public, R.string.category_genre_afrocuban),
            SearchCategory("celtic", getString(context, R.string.category_genre_celtic), "Celtic", Icons.Rounded.Forest, R.string.category_genre_celtic),
            SearchCategory("flamenco", getString(context, R.string.category_genre_flamenco), "Flamenco", Icons.Rounded.LocalFireDepartment, R.string.category_genre_flamenco),

            SearchCategory("jazz", getString(context, R.string.category_genre_jazz), "Jazz", Icons.Rounded.Piano, R.string.category_genre_jazz),
            SearchCategory("blues", getString(context, R.string.category_genre_blues), "Blues", Icons.Rounded.MusicNote, R.string.category_genre_blues),
            SearchCategory("classical", getString(context, R.string.category_genre_classical), "Classical", Icons.Rounded.AccountBalance, R.string.category_genre_classical),
            SearchCategory("country", getString(context, R.string.category_genre_country), "Country", Icons.Rounded.MusicNote, R.string.category_genre_country,
                artworkUrl = "https://i1.sndcdn.com/artworks-2wzLSZ6D3HMYR0T0-7DRJcg-original.png", borderColor = "orange", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:country"),
            SearchCategory("folk", getString(context, R.string.category_genre_folk), "Folk", Icons.Rounded.Forest, R.string.category_genre_folk,
                artworkUrl = "https://i1.sndcdn.com/artworks-IujP8FmENKj2GmfN-BGpzoQ-original.png", borderColor = "orange", systemPlaylistUrn = "soundcloud:system-playlists:trending-by-genre:folk"),
            SearchCategory("gospel", getString(context, R.string.category_genre_gospel), "Gospel", Icons.Rounded.Church, R.string.category_genre_gospel),
            SearchCategory("soundtrack", getString(context, R.string.category_genre_soundtrack), "Soundtrack", Icons.Rounded.Theaters, R.string.category_genre_soundtrack),
            SearchCategory("decades", getString(context, R.string.category_genre_decades), "80s 90s", Icons.Rounded.History, R.string.category_genre_decades),
            SearchCategory("family", getString(context, R.string.category_genre_family), "Kids", Icons.Rounded.ChildCare, R.string.category_genre_family)
        ).sortedBy { it.title }
    }

    fun getAllCategories(context: Context): List<SearchCategory> {
        return (getVisualCategories(context) + getMoods(context) + getGenres(context))
            .distinctBy { it.id }
    }

    fun findCategory(title: String, query: String, context: Context): SearchCategory? {
        val all = getAllCategories(context)
        return all.firstOrNull { it.id.equals(query, ignoreCase = true) || it.id.equals(title, ignoreCase = true) }
            ?: all.firstOrNull { it.query.equals(query, ignoreCase = true) }
            ?: all.firstOrNull { it.title.equals(title, ignoreCase = true) }
            ?: all.firstOrNull { it.title.contains(title, ignoreCase = true) || title.contains(it.title, ignoreCase = true) }
            ?: all.firstOrNull { it.query.contains(query, ignoreCase = true) || query.contains(it.query, ignoreCase = true) }
    }

    fun isMoodQuery(query: String, name: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        val n = name.lowercase(Locale.ROOT)
        val moodKeywords = listOf(
            "workout", "entraînement", "chill", "détente", "calm", "calme",
            "focus", "concentration", "energy", "énergie", "gaming", "winter", "hiver",
            "driving", "route", "romance", "party", "fête", "soirée", "sleep", "sommeil",
            "sad", "triste", "mélancolie", "feel good", "bonne humeur", "happy",
            "at home", "à la maison", "study", "révisions"
        )
        return moodKeywords.any { q.contains(it) || n.contains(it) }
    }

    fun parseSduiTrack(entityObj: JsonObject): Track? {
        val data = entityObj.getAsJsonObject("data") ?: entityObj
        val urn = data.get("urn")?.asString ?: ""
        val id = urn.substringAfterLast(":").toLongOrNull() ?: data.get("id")?.asLong ?: return null
        if (id <= 0) return null

        val title = data.get("title")?.asString
        val durationMs = data.get("duration")?.asLong ?: data.get("full_duration")?.asLong
        val genre = data.get("genre")?.asString
        val permalinkUrl = data.get("permalink_url")?.asString
        val waveformUrl = data.get("waveform_url")?.asString

        val artworkTemplate = data.get("artwork_url_template")?.asString
        val artworkUrl = data.get("artwork_url")?.asString
            ?: artworkTemplate?.replace("{size}", "t500x500")

        val embedded = data.getAsJsonObject("_embedded")
        val userObj = embedded?.getAsJsonObject("user")
        val user = if (userObj != null) parseSduiUser(userObj) else null

        val stats = embedded?.getAsJsonObject("stats")
        val playbackCount = stats?.get("playback_count")?.asInt ?: data.get("playback_count")?.asInt ?: 0
        val likesCount = stats?.get("likes_count")?.asInt ?: data.get("likes_count")?.asInt ?: 0

        val mediaObj = data.getAsJsonObject("media")
        val media = if (mediaObj != null) {
            try {
                AppUtils.gson.fromJson(mediaObj, Media::class.java)
            } catch (_: Exception) {
                null
            }
        } else null

        return Track(
            id = id,
            title = title,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            user = user,
            media = media,
            genre = genre,
            permalinkUrl = permalinkUrl,
            waveformUrl = waveformUrl,
            playbackCount = playbackCount,
            likesCount = likesCount
        )
    }

    fun parseSduiPlaylist(entityObj: JsonObject): Playlist? {
        val data = entityObj.getAsJsonObject("data") ?: entityObj
        val urn = data.get("urn")?.asString ?: ""
        val id = urn.substringAfterLast(":").toLongOrNull() ?: data.get("id")?.asLong ?: return null
        if (id <= 0) return null

        val title = data.get("title")?.asString
        val durationMs = data.get("duration")?.asLong
        val trackCount = data.get("track_count")?.asInt ?: 0
        val permalinkUrl = data.get("permalink_url")?.asString
        val isAlbum = data.get("is_album")?.asBoolean ?: false
        val setType = data.get("set_type")?.asString
        val playlistType = data.get("playlist_type")?.asString

        val artworkTemplate = data.get("artwork_url_template")?.asString
        val artworkUrl = data.get("artwork_url")?.asString
            ?: artworkTemplate?.replace("{size}", "t500x500")

        val embedded = data.getAsJsonObject("_embedded")
        val userObj = embedded?.getAsJsonObject("user")
        val user = if (userObj != null) parseSduiUser(userObj) else null

        return Playlist(
            id = id,
            title = title,
            durationMs = durationMs,
            trackCount = trackCount,
            artworkUrl = artworkUrl,
            calculatedArtworkUrl = artworkUrl,
            user = user,
            permalinkUrl = permalinkUrl,
            isAlbum = isAlbum,
            setType = setType,
            playlistType = playlistType
        )
    }

    fun parseSduiUser(entityObj: JsonObject): User? {
        val data = entityObj.getAsJsonObject("data") ?: entityObj
        val urn = data.get("urn")?.asString ?: ""
        val id = urn.substringAfterLast(":").toLongOrNull() ?: data.get("id")?.asLong ?: return null
        if (id <= 0) return null

        val username = data.get("username")?.asString
        val avatarTemplate = data.get("avatar_url_template")?.asString
        val avatarUrl = data.get("avatar_url")?.asString
            ?: avatarTemplate?.replace("{size}", "t500x500")
        val verified = data.get("verified")?.asBoolean ?: false
        val followersCount = data.get("followers_count")?.asInt ?: 0

        return User(
            id = id,
            username = username,
            avatarUrl = avatarUrl,
            verified = verified,
            followersCount = followersCount
        )
    }

    fun parseSduiLandingCategories(landingJson: JsonObject, context: Context): List<SearchCategory> {
        val sections = landingJson.getAsJsonArray("sections") ?: return emptyList()
        for (secElem in sections) {
            val secObj = secElem.asJsonObject ?: continue
            if (secObj.get("urn")?.asString == "soundcloud:sections:search_genre_grid") {
                val dataObj = secObj.getAsJsonObject("data") ?: continue
                val itemsArr = dataObj.getAsJsonArray("items") ?: continue
                val result = mutableListOf<SearchCategory>()
                for (itElem in itemsArr) {
                    val itObj = itElem.asJsonObject ?: continue
                    val rawTitle = itObj.get("title")?.asString
                        ?: itObj.getAsJsonObject("action_link")?.get("caption")?.asString
                        ?: continue
                    val border = itObj.get("border_color")?.asString
                    val darkArt = itObj.get("image_large_dark")?.asString
                        ?: itObj.get("image_medium_dark")?.asString
                        ?: itObj.get("image_small_dark")?.asString
                    val lightArt = itObj.get("image_large_light")?.asString
                        ?: itObj.get("image_medium_light")?.asString
                        ?: itObj.get("image_small_light")?.asString

                    val matched = findCategory(rawTitle, rawTitle, context)
                    val id = matched?.id ?: rawTitle.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
                    val title = matched?.title ?: rawTitle
                    val query = matched?.query ?: rawTitle
                    val kind = matched?.kind ?: if (isMoodQuery(query, title)) CategoryKind.MOOD else CategoryKind.GENRE
                    val sysUrn = matched?.systemPlaylistUrn

                    result.add(
                        SearchCategory(
                            id = id,
                            title = title,
                            query = query,
                            artworkUrl = darkArt ?: matched?.artworkUrl,
                            artworkUrlLight = lightArt ?: matched?.artworkUrlLight,
                            borderColor = border ?: matched?.borderColor,
                            systemPlaylistUrn = sysUrn,
                            kind = kind
                        )
                    )
                }
                if (result.isNotEmpty()) return result
            }
        }
        return emptyList()
    }

    fun parseSduiCategoryDetails(detailJson: JsonObject): ParsedCategoryDetails {
        val entities = detailJson.getAsJsonObject("entities") ?: return ParsedCategoryDetails()
        val sections = detailJson.getAsJsonArray("sections") ?: return ParsedCategoryDetails()

        var headerTitle: String? = null
        var headerDark: String? = null
        var headerLight: String? = null
        val canonicalTracks = mutableListOf<Track>()
        val vibeTracks = mutableListOf<Track>()
        val curatedPlaylists = mutableListOf<Playlist>()
        val vibePlaylists = mutableListOf<Playlist>()
        val communityPlaylists = mutableListOf<Playlist>()
        val albums = mutableListOf<Playlist>()
        val vibeAlbums = mutableListOf<Playlist>()
        val artists = mutableListOf<User>()
        val vibeArtists = mutableListOf<User>()
        var introducingPlaylist: Playlist? = null
        val introducingTracks = mutableListOf<Track>()
        val discoverMoreTracks = mutableListOf<Track>()
        val filterChoices = mutableListOf<String>()

        for (sectionElem in sections) {
            val sectionObj = sectionElem.asJsonObject ?: continue
            val urn = sectionObj.get("urn")?.asString ?: ""
            val type = sectionObj.get("type")?.asString ?: ""
            val dataObj = sectionObj.getAsJsonObject("data") ?: continue

            if (urn.contains("search_filters_menu") || type == "horizontal_menu") {
                val choicesArr = dataObj.getAsJsonArray("choices")
                if (choicesArr != null) {
                    for (cElem in choicesArr) {
                        val key = cElem.asJsonObject?.get("key")?.asString
                        if (!key.isNullOrBlank()) {
                            filterChoices.add(key)
                        }
                    }
                }
            }

            if (urn.contains("page_header")) {
                headerTitle = dataObj.get("title")?.asString
                headerDark = dataObj.get("image_large_dark")?.asString ?: dataObj.get("image_medium_dark")?.asString
                headerLight = dataObj.get("image_large_light")?.asString ?: dataObj.get("image_medium_light")?.asString
            }

            // Playlist preview / Introducing spotlight (vibe_buzzing)
            if (type == "playlist_preview" || urn.contains("vibe_buzzing") || urn.contains("playlist_preview")) {
                val plUrn = dataObj.get("result")?.asString
                if (plUrn != null) {
                    val plEntity = entities.getAsJsonObject(plUrn)
                    if (plEntity != null) {
                        introducingPlaylist = parseSduiPlaylist(plEntity)
                    }
                }
                val tUrns = dataObj.getAsJsonArray("tracks")
                if (tUrns != null) {
                    for (tElem in tUrns) {
                        val tUrn = tElem.asString
                        val tEntity = entities.getAsJsonObject(tUrn) ?: continue
                        parseSduiTrack(tEntity)?.let { introducingTracks.add(it) }
                    }
                }
            }

            // Discover more tracks (vibe_tracks_more)
            if (urn.contains("vibe_tracks_more")) {
                val resultsArr = dataObj.getAsJsonArray("results")
                if (resultsArr != null) {
                    for (resElem in resultsArr) {
                        val resUrn = resElem.asString
                        val entityJson = entities.getAsJsonObject(resUrn) ?: continue
                        val entityType = entityJson.get("type")?.asString ?: ""
                        if (entityType == "track") {
                            parseSduiTrack(entityJson)?.let { discoverMoreTracks.add(it) }
                        }
                    }
                }
            }

            val resultsArr = dataObj.getAsJsonArray("results") ?: continue

            when {
                // Canonical trending tracks section (e.g. soundcloud:sections:search_tracks_with_genre_filter)
                urn.contains("search_tracks") -> {
                    for (resElem in resultsArr) {
                        val resUrn = resElem.asString
                        val entityJson = entities.getAsJsonObject(resUrn) ?: continue
                        val type = entityJson.get("type")?.asString ?: ""
                        if (type == "track") {
                            parseSduiTrack(entityJson)?.let { canonicalTracks.add(it) }
                        }
                    }
                }
                // Visual tile / preview section (e.g. soundcloud:sections:vibe_tracks, vibe_tracks_2v4)
                urn.contains("vibe_tracks") && !urn.contains("vibe_tracks_more") -> {
                    for (resElem in resultsArr) {
                        val resUrn = resElem.asString
                        val entityJson = entities.getAsJsonObject(resUrn) ?: continue
                        val type = entityJson.get("type")?.asString ?: ""
                        when (type) {
                            "track" -> parseSduiTrack(entityJson)?.let { vibeTracks.add(it) }
                            "playlist" -> parseSduiPlaylist(entityJson)?.let {
                                if (it.isRealAlbum) vibeAlbums.add(it) else vibePlaylists.add(it)
                            }
                            "user" -> parseSduiUser(entityJson)?.let { vibeArtists.add(it) }
                        }
                    }
                }
                urn.contains("curated_playlists") -> {
                    for (resElem in resultsArr) {
                        val resUrn = resElem.asString
                        val entityJson = entities.getAsJsonObject(resUrn) ?: continue
                        parseSduiPlaylist(entityJson)?.let {
                            if (!it.isRealAlbum) curatedPlaylists.add(it)
                        }
                    }
                }
                urn.contains("search_playlists") -> {
                    for (resElem in resultsArr) {
                        val resUrn = resElem.asString
                        val entityJson = entities.getAsJsonObject(resUrn) ?: continue
                        parseSduiPlaylist(entityJson)?.let {
                            if (!it.isRealAlbum) communityPlaylists.add(it)
                        }
                    }
                }
                urn.contains("albums") -> {
                    for (resElem in resultsArr) {
                        val resUrn = resElem.asString
                        val entityJson = entities.getAsJsonObject(resUrn) ?: continue
                        parseSduiPlaylist(entityJson)?.let { albums.add(it) }
                    }
                }
                urn.contains("top_users") || urn.contains("search_users") -> {
                    for (resElem in resultsArr) {
                        val resUrn = resElem.asString
                        val entityJson = entities.getAsJsonObject(resUrn) ?: continue
                        parseSduiUser(entityJson)?.let { artists.add(it) }
                    }
                }
            }
        }

        // Canonical trending tracks take absolute priority so rank #1 matches the official SoundCloud app!
        val finalTracks = if (canonicalTracks.isNotEmpty()) {
            (canonicalTracks + vibeTracks).distinctBy { it.id }
        } else {
            vibeTracks.distinctBy { it.id }
        }

        return ParsedCategoryDetails(
            title = headerTitle,
            headerDarkArtwork = headerDark,
            headerLightArtwork = headerLight,
            tracks = finalTracks,
            vibeTracks = vibeTracks.distinctBy { it.id },
            curatedPlaylists = (curatedPlaylists + vibePlaylists).distinctBy { it.id },
            communityPlaylists = communityPlaylists.distinctBy { it.id },
            albums = (albums + vibeAlbums).distinctBy { it.id },
            artists = (artists + vibeArtists).distinctBy { it.id },
            introducingPlaylist = introducingPlaylist,
            introducingTracks = introducingTracks.distinctBy { it.id },
            discoverMoreTracks = discoverMoreTracks.distinctBy { it.id },
            filterChoices = filterChoices
        )
    }
}
