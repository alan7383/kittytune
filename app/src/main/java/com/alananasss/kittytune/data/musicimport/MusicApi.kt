package com.alananasss.kittytune.data.musicimport

enum class MusicApi(val providerName: String) {
    SPOTIFY("spotify"),
    APPLE_MUSIC("appleMusic"),
    YOUTUBE_MUSIC("youtube"),
    DEEZER("deezer"),
    TIDAL("tidal"),
    AMAZON_MUSIC("amazonMusic"),
    BOOMPLAY("boomplay");

    companion object {
        fun fromProviderName(providerName: String): MusicApi? =
            entries.firstOrNull { it.providerName == providerName }
    }
}
