package com.alananasss.kittytune.audio.providers

enum class AudioProviderOrderItem {
    QOBUZ,
    TIDAL,
    DEEZER,
    YOUTUBE_MUSIC,
    SOUNDCLOUD;

    fun isPlaybackProvider(): Boolean = true
}

object AudioProviderOrder {
    val Default: List<AudioProviderOrderItem> =
        listOf(
            AudioProviderOrderItem.QOBUZ,
            AudioProviderOrderItem.TIDAL,
            AudioProviderOrderItem.DEEZER,
            AudioProviderOrderItem.YOUTUBE_MUSIC,
            AudioProviderOrderItem.SOUNDCLOUD,
        )

    fun serialize(providers: List<AudioProviderOrderItem>): String =
        normalize(providers).joinToString(",") { it.name }

    fun deserialize(value: String?): List<AudioProviderOrderItem> =
        normalize(
            value
                ?.split(',')
                ?.mapNotNull { raw -> AudioProviderOrderItem.entries.find { it.name == raw.trim() } }
                .orEmpty(),
        )

    private fun normalize(providers: List<AudioProviderOrderItem>): List<AudioProviderOrderItem> =
        (providers + Default)
            .filter { it.isPlaybackProvider() }
            .distinct()
}
