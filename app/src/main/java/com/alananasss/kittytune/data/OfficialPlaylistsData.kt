    package com.alananasss.kittytune.data
    
    // this object holds the usernames for soundcloud's official regional playlist accounts.
    object OfficialPlaylistsData {
    
        data class PlaylistSource(
            val countryName: String,
            val flagEmoji: String,
            val soundCloudUsername: String
        )
    
        // a curated list of official soundcloud playlist accounts by region.
        val sources = listOf(
            PlaylistSource("Global", "🌍", "sc-playlists"),
            PlaylistSource("United States", "🇺🇸", "sc-playlists-us"),
            PlaylistSource("France", "🇫🇷", "sc-playlists-fr"),
            PlaylistSource("Germany", "🇩🇪", "sc-playlists-de"),
            PlaylistSource("United Kingdom", "🇬🇧", "sc-playlists-gb"),
            PlaylistSource("Canada", "🇨🇦", "sc-playlists-ca"),
            PlaylistSource("Australia", "🇦🇺", "sc-playlists-au"),
            PlaylistSource("Japan", "🇯🇵", "sc-playlists-jp"),
            PlaylistSource("Brazil", "🇧🇷", "sc-playlists-br"),
            PlaylistSource("India", "🇮🇳", "sc-playlists-in"),
            PlaylistSource("Indonesia", "🇮🇩", "sc-playlists-id"),
            PlaylistSource("Netherlands", "🇳🇱", "sc-playlists-nl"),
            PlaylistSource("Poland", "🇵🇱", "sc-playlists-pl"),
            PlaylistSource("SoundCloud Stories", "📖", "soundcloud-stories") // special case
        ).sortedBy { it.countryName }
    }


