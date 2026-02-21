    package com.alananasss.kittytune.data
    
    import android.content.Context
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.ui.graphics.vector.ImageVector
    import com.alananasss.kittytune.R
    
    // shared data model for categories
    data class SearchCategory(
        val id: String,
        val title: String,
        val query: String,
        val icon: ImageVector
    )
    
    object GenreData {
    
        // helper to get string resources
        private fun getString(context: Context, resId: Int): String {
            return context.getString(resId)
        }
    
        fun getMoods(context: Context): List<SearchCategory> {
            return listOf(
                SearchCategory("feelgood", getString(context, R.string.category_mood_feelgood), "Feel Good", Icons.Rounded.Mood),
                SearchCategory("calm", getString(context, R.string.category_mood_calm), "Calm Relax", Icons.Rounded.Spa),
                SearchCategory("focus", getString(context, R.string.category_mood_focus), "Focus Study", Icons.Rounded.Psychology),
                SearchCategory("energy", getString(context, R.string.category_mood_energy), "Energy", Icons.Rounded.LocalFireDepartment),
                SearchCategory("workout", getString(context, R.string.category_mood_workout), "Workout", Icons.Rounded.FitnessCenter),
                SearchCategory("gaming", getString(context, R.string.category_mood_gaming), "Gaming", Icons.Rounded.SportsEsports),
                SearchCategory("winter", getString(context, R.string.category_mood_winter), "Winter", Icons.Rounded.AcUnit),
                SearchCategory("driving", getString(context, R.string.category_mood_driving), "Driving", Icons.Rounded.DirectionsCar),
                SearchCategory("romance", getString(context, R.string.category_mood_romance), "Romantic", Icons.Rounded.Favorite),
                SearchCategory("party", getString(context, R.string.category_mood_party), "Party", Icons.Rounded.Celebration),
                SearchCategory("sleep", getString(context, R.string.category_mood_sleep), "Sleep", Icons.Rounded.Bedtime),
                SearchCategory("sad", getString(context, R.string.category_mood_sad), "Sad", Icons.Rounded.SentimentVeryDissatisfied)
            )
        }
    
        fun getGenres(context: Context): List<SearchCategory> {
            return listOf(
                SearchCategory("hiphop", getString(context, R.string.category_genre_hiphop), "Hip Hop", Icons.Rounded.Mic),
                SearchCategory("rapfr", getString(context, R.string.category_genre_rapfr), "Rap FR", Icons.Rounded.Mic),
                SearchCategory("phonk", getString(context, R.string.category_genre_phonk_fix), "Phonk", Icons.Rounded.TimeToLeave),
                SearchCategory("rnb", getString(context, R.string.category_genre_rnb), "R&B", Icons.Rounded.FavoriteBorder),
                SearchCategory("funk", getString(context, R.string.category_genre_funk), "Funk", Icons.Rounded.Nightlife),
                SearchCategory("disco", getString(context, R.string.category_genre_disco), "Disco", Icons.Rounded.Album),
    
                SearchCategory("dance", getString(context, R.string.category_genre_dance), "EDM", Icons.Rounded.FlashOn),
                SearchCategory("house", getString(context, R.string.category_genre_house), "House", Icons.Rounded.Nightlife),
                SearchCategory("hardstyle", getString(context, R.string.category_genre_hardstyle), "Hardstyle", Icons.Rounded.Bolt),
                SearchCategory("trance", getString(context, R.string.category_genre_trance), "Trance", Icons.Rounded.Waves),
                SearchCategory("dubstep", getString(context, R.string.category_genre_dubstep), "Dubstep", Icons.Rounded.GraphicEq),
                SearchCategory("pluggnb", getString(context, R.string.category_genre_pluggnb), "Pluggnb", Icons.Rounded.Cloud),
                SearchCategory("dreamcore", getString(context, R.string.category_genre_dreamcore), "Dreamcore", Icons.Rounded.AutoAwesome),
                SearchCategory("glitchcore", getString(context, R.string.category_genre_glitchcore), "Glitchcore", Icons.Rounded.SdCardAlert),
                SearchCategory("eurobeat", getString(context, R.string.category_genre_eurobeat), "Eurobeat", Icons.Rounded.Bolt),
                SearchCategory("cloudrap", getString(context, R.string.category_genre_cloudrap), "Cloud Rap", Icons.Rounded.WbCloudy),
                SearchCategory("drumandbass", getString(context, R.string.category_genre_drumandbass), "Drum and Bass", Icons.Rounded.FastForward),
                SearchCategory("jungle", getString(context, R.string.category_genre_jungle), "Jungle", Icons.Rounded.Forest),
                SearchCategory("breakcore", getString(context, R.string.category_genre_breakcore), "Breakcore", Icons.Rounded.BrokenImage),
                SearchCategory("garage", getString(context, R.string.category_genre_garage), "UK Garage", Icons.Rounded.Garage),
                SearchCategory("idm", getString(context, R.string.category_genre_idm), "IDM", Icons.Rounded.Memory),
    
                SearchCategory("pop", getString(context, R.string.category_genre_pop), "Pop", Icons.Rounded.Star),
                SearchCategory("hyperpop", getString(context, R.string.category_genre_hyperpop), "Hyperpop", Icons.Rounded.Flare),
                SearchCategory("scenecore", getString(context, R.string.category_genre_scenecore), "Scenecore", Icons.Rounded.Style),
                SearchCategory("digicore", getString(context, R.string.category_genre_digicore), "Digicore", Icons.Rounded.DataObject),
                SearchCategory("vocaloid", getString(context, R.string.category_genre_vocaloid), "Vocaloid", Icons.Rounded.Face),
                SearchCategory("lolicore", getString(context, R.string.category_genre_lolicore), "Lolicore", Icons.Rounded.ChildCare),
                SearchCategory("anime", getString(context, R.string.category_genre_anime), "Anime", Icons.Rounded.Animation),
                SearchCategory("weeb", getString(context, R.string.category_genre_weeb), "Otaku", Icons.Rounded.AutoAwesome),
                SearchCategory("nightcore", getString(context, R.string.category_genre_nightcore), "Nightcore", Icons.Rounded.HistoryToggleOff),
                SearchCategory("bedroompop", getString(context, R.string.category_genre_bedroompop), "Bedroom Pop", Icons.Rounded.Bed),
                SearchCategory("popjp", getString(context, R.string.category_genre_popjp), "J-Pop", Icons.Rounded.MusicNote),
                SearchCategory("kpop", getString(context, R.string.category_genre_kpop), "K-Pop", Icons.Rounded.StarBorder),
                SearchCategory("popfr", getString(context, R.string.category_genre_popfr), "Variété Française", Icons.Rounded.MusicNote),
                SearchCategory("urbanfr", getString(context, R.string.category_genre_urbanfr), "Pop Urbaine", Icons.Rounded.Mic),
    
                SearchCategory("rock", getString(context, R.string.category_genre_rock), "Rock", Icons.Rounded.Whatshot),
                SearchCategory("alt", getString(context, R.string.category_genre_alt), "Alternative", Icons.Rounded.Album),
                SearchCategory("metal", getString(context, R.string.category_genre_metal), "Metal", Icons.Rounded.Bolt),
                SearchCategory("emo", getString(context, R.string.category_genre_emo), "Emo", Icons.Rounded.SentimentDissatisfied),
                SearchCategory("grunge", getString(context, R.string.category_genre_grunge), "Grunge", Icons.Rounded.MusicOff),
                SearchCategory("shoegaze", getString(context, R.string.category_genre_shoegaze), "Shoegaze", Icons.Rounded.Waves),
    
                SearchCategory("lofi", getString(context, R.string.category_genre_lofi), "Lofi", Icons.Rounded.LocalCafe),
                SearchCategory("ambient", getString(context, R.string.category_genre_ambient), "Ambient", Icons.Rounded.WbCloudy),
                SearchCategory("vaporwave", getString(context, R.string.category_genre_vaporwave), "Vaporwave", Icons.Rounded.Computer),
                SearchCategory("synthwave", getString(context, R.string.category_genre_synthwave), "Synthwave", Icons.Rounded.Brightness4),
    
                SearchCategory("latin", getString(context, R.string.category_genre_latin), "Latin", Icons.Rounded.Public),
                SearchCategory("afro", getString(context, R.string.category_genre_afro), "Afrobeat", Icons.Rounded.Public),
                SearchCategory("reggae", getString(context, R.string.category_genre_reggae), "Reggae", Icons.Rounded.Public),
                SearchCategory("arabic", getString(context, R.string.category_genre_arabic), "Arabic", Icons.Rounded.MusicNote),
                SearchCategory("bollywood", getString(context, R.string.category_genre_bollywood), "Bollywood", Icons.Rounded.MusicNote),
                SearchCategory("brazil", getString(context, R.string.category_genre_brazil), "Brazil", Icons.Rounded.Public),
                SearchCategory("afrocuban", getString(context, R.string.category_genre_afrocuban), "Afro-Cuban", Icons.Rounded.Public),
                SearchCategory("celtic", getString(context, R.string.category_genre_celtic), "Celtic", Icons.Rounded.Forest),
                SearchCategory("flamenco", getString(context, R.string.category_genre_flamenco), "Flamenco", Icons.Rounded.LocalFireDepartment),
    
                SearchCategory("jazz", getString(context, R.string.category_genre_jazz), "Jazz", Icons.Rounded.Piano),
                SearchCategory("blues", getString(context, R.string.category_genre_blues), "Blues", Icons.Rounded.MusicNote),
                SearchCategory("classical", getString(context, R.string.category_genre_classical), "Classical", Icons.Rounded.AccountBalance),
                SearchCategory("country", getString(context, R.string.category_genre_country), "Country", Icons.Rounded.MusicNote),
                SearchCategory("folk", getString(context, R.string.category_genre_folk), "Folk", Icons.Rounded.Forest),
                SearchCategory("gospel", getString(context, R.string.category_genre_gospel), "Gospel", Icons.Rounded.Church),
                SearchCategory("soundtrack", getString(context, R.string.category_genre_soundtrack), "Soundtrack", Icons.Rounded.Theaters),
                SearchCategory("decades", getString(context, R.string.category_genre_decades), "80s 90s", Icons.Rounded.History),
                SearchCategory("family", getString(context, R.string.category_genre_family), "Kids", Icons.Rounded.ChildCare)
            ).sortedBy { it.title }
        }
    }


