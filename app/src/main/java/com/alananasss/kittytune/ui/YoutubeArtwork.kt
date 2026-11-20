package com.alananasss.kittytune.ui

private const val MIN_LARGE_ARTWORK_PIXELS = 240
private const val YOUTUBE_MUSIC_COVER_SIZE = 1080
private val MUSIC_IMAGE_SIZE = Regex(
    """^(https://lh\d+\.(?:googleusercontent\.com|ggpht\.com)/[^?#=]+)=(?:w(\d+)-h(\d+)|s(\d+))(?=[-?#]|$)"""
)
private val VIDEO_THUMBNAIL = Regex(
    """^(https://i\.ytimg\.com/vi(?:_webp)?/[A-Za-z0-9_-]{11}/)(?:default|mqdefault|hqdefault|sddefault)\.(jpg|webp)(?:[?#].*)?$"""
)

/** Higher-resolution alternatives for a large cover. The caller can still load the original URL. */
internal fun youtubeArtworkUpgrades(url: String, displayPixels: Int): List<String> {
    if (displayPixels < MIN_LARGE_ARTWORK_PIXELS) return emptyList()

    MUSIC_IMAGE_SIZE.find(url)?.let { match ->
        val size = match.groupValues[4].toIntOrNull()
        val resized = if (size != null) {
            if (size !in 1 until YOUTUBE_MUSIC_COVER_SIZE) return emptyList()
            "s$YOUTUBE_MUSIC_COVER_SIZE"
        } else {
            val width = match.groupValues[2].toIntOrNull() ?: return emptyList()
            val height = match.groupValues[3].toIntOrNull() ?: return emptyList()
            val longest = maxOf(width, height)
            if (minOf(width, height) <= 0 || longest >= YOUTUBE_MUSIC_COVER_SIZE) return emptyList()
            val newWidth = (width * YOUTUBE_MUSIC_COVER_SIZE / longest).coerceAtLeast(1)
            val newHeight = (height * YOUTUBE_MUSIC_COVER_SIZE / longest).coerceAtLeast(1)
            "w$newWidth-h$newHeight"
        }
        return listOf(match.groupValues[1] + "=" + resized + url.substring(match.range.last + 1))
    }

    val (prefix, format) = VIDEO_THUMBNAIL.matchEntire(url)?.destructured ?: return emptyList()
    return listOf(prefix + "maxresdefault.$format", prefix + "hq720.$format")
}
