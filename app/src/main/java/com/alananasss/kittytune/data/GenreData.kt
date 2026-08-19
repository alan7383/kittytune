package com.alananasss.kittytune.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.R
import com.alananasss.kittytune.utils.LocaleUtils

data class SearchCategory(
    val id: String,
    val title: String,
    val query: String,
    val icon: ImageVector,
    val titleRes: Int = 0
)

object GenreData {

    private fun getString(context: Context, resId: Int): String {
        return LocaleUtils.updateBaseContextLocale(context).getString(resId)
    }

    fun getMoods(context: Context): List<SearchCategory> {
        return listOf(
            SearchCategory("feelgood", getString(context, R.string.category_mood_feelgood), "Feel Good", Icons.Rounded.Mood, R.string.category_mood_feelgood),
            SearchCategory("calm", getString(context, R.string.category_mood_calm), "Calm Relax", Icons.Rounded.Spa, R.string.category_mood_calm),
            SearchCategory("focus", getString(context, R.string.category_mood_focus), "Focus Study", Icons.Rounded.Psychology, R.string.category_mood_focus),
            SearchCategory("energy", getString(context, R.string.category_mood_energy), "Energy", Icons.Rounded.LocalFireDepartment, R.string.category_mood_energy),
            SearchCategory("workout", getString(context, R.string.category_mood_workout), "Workout", Icons.Rounded.FitnessCenter, R.string.category_mood_workout),
            SearchCategory("gaming", getString(context, R.string.category_mood_gaming), "Gaming", Icons.Rounded.SportsEsports, R.string.category_mood_gaming),
            SearchCategory("winter", getString(context, R.string.category_mood_winter), "Winter", Icons.Rounded.AcUnit, R.string.category_mood_winter),
            SearchCategory("driving", getString(context, R.string.category_mood_driving), "Driving", Icons.Rounded.DirectionsCar, R.string.category_mood_driving),
            SearchCategory("romance", getString(context, R.string.category_mood_romance), "Romantic", Icons.Rounded.Favorite, R.string.category_mood_romance),
            SearchCategory("party", getString(context, R.string.category_mood_party), "Party", Icons.Rounded.Celebration, R.string.category_mood_party),
            SearchCategory("sleep", getString(context, R.string.category_mood_sleep), "Sleep", Icons.Rounded.Bedtime, R.string.category_mood_sleep),
            SearchCategory("sad", getString(context, R.string.category_mood_sad), "Sad", Icons.Rounded.SentimentVeryDissatisfied, R.string.category_mood_sad)
        )
    }

    fun getGenres(context: Context): List<SearchCategory> {
        return listOf(
            SearchCategory("hiphop", getString(context, R.string.category_genre_hiphop), "Hip Hop", Icons.Rounded.Mic, R.string.category_genre_hiphop),
            SearchCategory("rapfr", getString(context, R.string.category_genre_rapfr), "Rap FR", Icons.Rounded.Mic, R.string.category_genre_rapfr),
            SearchCategory("phonk", getString(context, R.string.category_genre_phonk_fix), "Phonk", Icons.Rounded.TimeToLeave, R.string.category_genre_phonk_fix),
            SearchCategory("rnb", getString(context, R.string.category_genre_rnb), "R&B", Icons.Rounded.FavoriteBorder, R.string.category_genre_rnb),
            SearchCategory("funk", getString(context, R.string.category_genre_funk), "Funk", Icons.Rounded.Nightlife, R.string.category_genre_funk),
            SearchCategory("disco", getString(context, R.string.category_genre_disco), "Disco", Icons.Rounded.Album, R.string.category_genre_disco),

            SearchCategory("dance", getString(context, R.string.category_genre_dance), "EDM", Icons.Rounded.FlashOn, R.string.category_genre_dance),
            SearchCategory("house", getString(context, R.string.category_genre_house), "House", Icons.Rounded.Nightlife, R.string.category_genre_house),
            SearchCategory("hardstyle", getString(context, R.string.category_genre_hardstyle), "Hardstyle", Icons.Rounded.Bolt, R.string.category_genre_hardstyle),
            SearchCategory("trance", getString(context, R.string.category_genre_trance), "Trance", Icons.Rounded.Waves, R.string.category_genre_trance),
            SearchCategory("dubstep", getString(context, R.string.category_genre_dubstep), "Dubstep", Icons.Rounded.GraphicEq, R.string.category_genre_dubstep),
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

            SearchCategory("pop", getString(context, R.string.category_genre_pop), "Pop", Icons.Rounded.Star, R.string.category_genre_pop),
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

            SearchCategory("rock", getString(context, R.string.category_genre_rock), "Rock", Icons.Rounded.Whatshot, R.string.category_genre_rock),
            SearchCategory("alt", getString(context, R.string.category_genre_alt), "Alternative", Icons.Rounded.Album, R.string.category_genre_alt),
            SearchCategory("metal", getString(context, R.string.category_genre_metal), "Metal", Icons.Rounded.Bolt, R.string.category_genre_metal),
            SearchCategory("emo", getString(context, R.string.category_genre_emo), "Emo", Icons.Rounded.SentimentDissatisfied, R.string.category_genre_emo),
            SearchCategory("grunge", getString(context, R.string.category_genre_grunge), "Grunge", Icons.Rounded.MusicOff, R.string.category_genre_grunge),
            SearchCategory("shoegaze", getString(context, R.string.category_genre_shoegaze), "Shoegaze", Icons.Rounded.Waves, R.string.category_genre_shoegaze),

            SearchCategory("lofi", getString(context, R.string.category_genre_lofi), "Lofi", Icons.Rounded.LocalCafe, R.string.category_genre_lofi),
            SearchCategory("ambient", getString(context, R.string.category_genre_ambient), "Ambient", Icons.Rounded.WbCloudy, R.string.category_genre_ambient),
            SearchCategory("vaporwave", getString(context, R.string.category_genre_vaporwave), "Vaporwave", Icons.Rounded.Computer, R.string.category_genre_vaporwave),
            SearchCategory("synthwave", getString(context, R.string.category_genre_synthwave), "Synthwave", Icons.Rounded.Brightness4, R.string.category_genre_synthwave),

            SearchCategory("latin", getString(context, R.string.category_genre_latin), "Latin", Icons.Rounded.Public, R.string.category_genre_latin),
            SearchCategory("afro", getString(context, R.string.category_genre_afro), "Afrobeat", Icons.Rounded.Public, R.string.category_genre_afro),
            SearchCategory("reggae", getString(context, R.string.category_genre_reggae), "Reggae", Icons.Rounded.Public, R.string.category_genre_reggae),
            SearchCategory("arabic", getString(context, R.string.category_genre_arabic), "Arabic", Icons.Rounded.MusicNote, R.string.category_genre_arabic),
            SearchCategory("bollywood", getString(context, R.string.category_genre_bollywood), "Bollywood", Icons.Rounded.MusicNote, R.string.category_genre_bollywood),
            SearchCategory("brazil", getString(context, R.string.category_genre_brazil), "Brazil", Icons.Rounded.Public, R.string.category_genre_brazil),
            SearchCategory("afrocuban", getString(context, R.string.category_genre_afrocuban), "Afro-Cuban", Icons.Rounded.Public, R.string.category_genre_afrocuban),
            SearchCategory("celtic", getString(context, R.string.category_genre_celtic), "Celtic", Icons.Rounded.Forest, R.string.category_genre_celtic),
            SearchCategory("flamenco", getString(context, R.string.category_genre_flamenco), "Flamenco", Icons.Rounded.LocalFireDepartment, R.string.category_genre_flamenco),

            SearchCategory("jazz", getString(context, R.string.category_genre_jazz), "Jazz", Icons.Rounded.Piano, R.string.category_genre_jazz),
            SearchCategory("blues", getString(context, R.string.category_genre_blues), "Blues", Icons.Rounded.MusicNote, R.string.category_genre_blues),
            SearchCategory("classical", getString(context, R.string.category_genre_classical), "Classical", Icons.Rounded.AccountBalance, R.string.category_genre_classical),
            SearchCategory("country", getString(context, R.string.category_genre_country), "Country", Icons.Rounded.MusicNote, R.string.category_genre_country),
            SearchCategory("folk", getString(context, R.string.category_genre_folk), "Folk", Icons.Rounded.Forest, R.string.category_genre_folk),
            SearchCategory("gospel", getString(context, R.string.category_genre_gospel), "Gospel", Icons.Rounded.Church, R.string.category_genre_gospel),
            SearchCategory("soundtrack", getString(context, R.string.category_genre_soundtrack), "Soundtrack", Icons.Rounded.Theaters, R.string.category_genre_soundtrack),
            SearchCategory("decades", getString(context, R.string.category_genre_decades), "80s 90s", Icons.Rounded.History, R.string.category_genre_decades),
            SearchCategory("family", getString(context, R.string.category_genre_family), "Kids", Icons.Rounded.ChildCare, R.string.category_genre_family)
        ).sortedBy { it.title }
    }
}
