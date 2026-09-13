package com.alananasss.kittytune.data.lyrics.providers

enum class PreferredLyricsProvider(val displayName: String) {
    BETTER_LYRICS("BetterLyrics"),
    BETTER_LYRICS_PORTATO("BetterLyrics (QQ)"),
    YOULY_PLUS("YouLyPlus"),
    LRCLIB("LrcLib"),
    KUGOU("KuGou"),
    MEGALOBIZ("Megalobiz"),
    SIMPMUSIC("SimpMusic"),
    UNISON("Unison"),
    PAXSENIX_APPLE_MUSIC("Paxsenix (Apple Music)"),
    PAXSENIX_SPOTIFY("Paxsenix (Spotify)"),
    PAXSENIX_MUSIXMATCH("Paxsenix (Musixmatch)"),
    YOUTUBE_SUBTITLE("YouTube Subtitle"),
    YOUTUBE("YouTube Music"),
    MUSIXMATCH("Musixmatch"),
    GENIUS("Genius");

    companion object {
        fun fromName(name: String?): PreferredLyricsProvider? =
            entries.find { it.name.equals(name, ignoreCase = true) }
    }
}

val DefaultLyricsProviderOrder = listOf(
    PreferredLyricsProvider.MUSIXMATCH,
    PreferredLyricsProvider.BETTER_LYRICS,
    PreferredLyricsProvider.BETTER_LYRICS_PORTATO,
    PreferredLyricsProvider.YOULY_PLUS,
    PreferredLyricsProvider.LRCLIB,
    PreferredLyricsProvider.KUGOU,
    PreferredLyricsProvider.MEGALOBIZ,
    PreferredLyricsProvider.SIMPMUSIC,
    PreferredLyricsProvider.UNISON,
    PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC,
    PreferredLyricsProvider.PAXSENIX_SPOTIFY,
    PreferredLyricsProvider.PAXSENIX_MUSIXMATCH,
    PreferredLyricsProvider.YOUTUBE_SUBTITLE,
    PreferredLyricsProvider.YOUTUBE,
    PreferredLyricsProvider.GENIUS,
)

fun deserializeLyricsProviderOrder(orderStr: String?): List<PreferredLyricsProvider> {
    if (orderStr.isNullOrBlank()) return DefaultLyricsProviderOrder

    val parsed = orderStr.split(",")
        .mapNotNull { PreferredLyricsProvider.fromName(it.trim()) }
        .distinct()

    val missing = DefaultLyricsProviderOrder.filterNot { it in parsed }
    return parsed + missing
}

fun serializeLyricsProviderOrder(order: List<PreferredLyricsProvider>): String {
    return order.joinToString(",") { it.name }
}

interface LyricsProvider {
    val id: PreferredLyricsProvider
    val name: String get() = id.displayName

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String>
}
