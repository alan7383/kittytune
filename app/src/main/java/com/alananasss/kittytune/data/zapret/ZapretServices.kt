package com.alananasss.kittytune.data.zapret

/**
 * The services KittyTune talks to, each with a URL to probe and the domains the DPI
 * bypass has to handle for it.
 *
 * Ported from KittyTune Desktop: domains are the registrable ones, host matching covers
 * subdomains, so `sndcdn.com` covers every `i1.`, `cf-hls-media.` and so on.
 */
data class ZapretService(
    val id: String,
    val name: String,
    /**
     * The endpoints the app actually talks to. All of them have to answer with data: a site's front page can
     * load while the API behind search and playback is blocked, which is what made "works" wrong before.
     */
    val probeUrls: List<String>,
    val domains: List<String>,
)

object ZapretServices {
    val ALL = listOf(
        ZapretService(
            "soundcloud", "SoundCloud",
            listOf(
                "https://soundcloud.com/",
                "https://api-v2.soundcloud.com/search?q=a",
                "https://i1.sndcdn.com/",
                // Distinct edges the app really uses: account actions (likes, profile),
                // playlist import (graphql) and session refresh. Any status counts, even
                // a 401 without token — only a timeout or reset means blocked.
                "https://api-mobile.soundcloud.com/me",
                "https://graph.soundcloud.com/graphql",
                "https://api-auth.soundcloud.com/",
            ),
            listOf("soundcloud.com", "sndcdn.com", "soundcloud.cloud"),
        ),
        ZapretService(
            "youtube", "YouTube Music",
            listOf(
                "https://music.youtube.com/",
                "https://www.youtube.com/youtubei/v1/search",
                "https://i.ytimg.com/",
                // The playback edge: throttled on its own while the front page loads fine.
                "https://www.googlevideo.com/",
            ),
            listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com", "youtubei.googleapis.com", "kavin.rocks"),
        ),
        ZapretService(
            "spotify", "Spotify",
            listOf(
                "https://open.spotify.com/",
                "https://api-partner.spotify.com/pathfinder/v1/query",
                "https://spclient.wg.spotify.com/",
                "https://i.scdn.co/",
                // Recommendations power radio and mixes; 401 without token still answers.
                "https://api.spotify.com/v1/recommendations?seed_tracks=4uLU6hMCjMI75M1A2tKUQm&limit=1",
            ),
            listOf("spotify.com", "scdn.co", "spotifycdn.com"),
        ),
        ZapretService(
            "apple", "Apple Music",
            listOf("https://music.apple.com/", "https://amp-api.music.apple.com/v1/catalog/us/search?term=a", "https://mzstatic.com/"),
            listOf("music.apple.com", "amp-api.music.apple.com", "mzstatic.com"),
        ),
        ZapretService("deezer", "Deezer", listOf("https://api.deezer.com/chart", "https://www.deezer.com/"), listOf("deezer.com", "dzcdn.net")),
        ZapretService("tidal", "TIDAL", listOf("https://tidal.com/", "https://api.tidal.com/v1/", "https://auth.tidal.com/v1/oauth2/token"), listOf("tidal.com", "tidalhifi.com")),
        ZapretService("qobuz", "Qobuz", listOf("https://www.qobuz.com/", "https://www.qobuz.com/api.json/0.2/"), listOf("qobuz.com")),
        ZapretService(
            "lyrics", "Lyrics (LRCLIB, Musixmatch, Genius…)",
            listOf("https://lrclib.net/api/search?q=hello", "https://apic-desktop.musixmatch.com/", "https://api.genius.com/"),
            listOf("lrclib.net", "musixmatch.com", "genius.com", "paxsenix.org", "boidu.dev", "megalobiz.com", "simpmusic.org", "binimum.org", "prjktla.my.id", "atomix.one"),
        ),
        ZapretService(
            "helpers", "Helper APIs (Hugging Face, Render, Vercel)",
            listOf("https://huggingface.co/", "https://vercel.app/", "https://picsum.photos/200"),
            listOf("hf.space", "huggingface.co", "onrender.com", "vercel.app", "workers.dev", "picsum.photos"),
        ),
        ZapretService("discord", "Discord", listOf("https://discord.com/api/v9/gateway", "https://cdn.discordapp.com/"), listOf("discord.com", "discordapp.com", "discord.gg", "discord.media")),
        ZapretService("github", "GitHub (updates)", listOf("https://api.github.com/", "https://objects.githubusercontent.com/"), listOf("github.com", "githubusercontent.com")),
        ZapretService(
            "vk", "VK",
            listOf("https://vk.com/", "https://api.vk.com/method/users.get?v=5.131"),
            listOf("vk.com", "vk.ru"),
        ),
        ZapretService(
            "shazam", "Shazam (recognition)",
            listOf("https://www.shazam.com/", "https://amp.shazam.com/"),
            listOf("shazam.com"),
        ),
        ZapretService(
            "kugou", "KuGou (lyrics)",
            listOf("https://www.kugou.com/", "https://mobileservice.kugou.com/api/v3/search/song?keyword=test&pagesize=1", "https://lyrics.kugou.com/"),
            listOf("kugou.com"),
        ),
        ZapretService(
            "songlink", "SongLink (matching)",
            listOf("https://song.link/", "https://api.song.link/v1-alpha.1/links?url=https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQm"),
            listOf("song.link"),
        ),
        ZapretService(
            "translator", "Translator (Google)",
            listOf("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=hello"),
            listOf("translate.googleapis.com"),
        ),
    )
}
