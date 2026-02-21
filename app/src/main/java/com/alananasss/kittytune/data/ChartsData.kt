    package com.alananasss.kittytune.data
    
    object ChartsData {
        data class ChartDefinition(
            val countryName: String,
            val countryCode: String,
            val flagEmoji: String,
            val playlistUrls: List<String>
        )
    
        val charts = listOf(
            ChartDefinition(
                countryName = "United States",
                countryCode = "US",
                flagEmoji = "🇺🇸",
                playlistUrls = listOf(
                    "https://soundcloud.com/music-charts-us/sets/all-music-genres",
                    "https://soundcloud.com/music-charts-us/sets/new-hot",
                    "https://soundcloud.com/music-charts-us/sets/pop",
                    "https://soundcloud.com/music-charts-us/sets/hip-hop",
                    "https://soundcloud.com/music-charts-us/sets/r-b",
                    "https://soundcloud.com/music-charts-us/sets/electronic",
                    "https://soundcloud.com/music-charts-us/sets/rock",
                    "https://soundcloud.com/music-charts-us/sets/country",
                    "https://soundcloud.com/music-charts-us/sets/latin",
                    "https://soundcloud.com/music-charts-us/sets/folk",
                    "https://soundcloud.com/music-charts-us/sets/artist-pro"
                )
            ),
            ChartDefinition(
                countryName = "United Kingdom",
                countryCode = "UK",
                flagEmoji = "🇬🇧",
                playlistUrls = listOf(
                    "https://soundcloud.com/music-charts-uk/sets/all-music-genres",
                    "https://soundcloud.com/music-charts-uk/sets/new-hot",
                    "https://soundcloud.com/music-charts-uk/sets/pop",
                    "https://soundcloud.com/music-charts-uk/sets/hip-hop",
                    "https://soundcloud.com/music-charts-uk/sets/dance",
                    "https://soundcloud.com/music-charts-uk/sets/r-b",
                    "https://soundcloud.com/music-charts-uk/sets/rock",
                    "https://soundcloud.com/music-charts-uk/sets/indie",
                    "https://soundcloud.com/music-charts-uk/sets/folk",
                    "https://soundcloud.com/music-charts-uk/sets/artist-pro"
                )
            )
        )
    }


