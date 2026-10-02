package com.alananasss.kittytune.audio.providers

enum class AudioProviderOrderItem {
    QOBUZ,
    TIDAL,
    DEEZER,
    YOUTUBE_MUSIC,
    SOUNDCLOUD;

    fun isPlaybackProvider(): Boolean = true

    /**
     * Whether the user may switch this provider off.
     *
     * Qobuz, TIDAL and Deezer can serve FLAC / Hi-Res streams that weigh hundreds of
     * megabytes per hour, so they can be disabled to save mobile data. SoundCloud and
     * YouTube Music are the lightweight base sources and stay always available.
     */
    fun isDisableable(): Boolean = this == QOBUZ || this == TIDAL || this == DEEZER
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

    /** Providers that may be switched off (see [AudioProviderOrderItem.isDisableable]). */
    val Disableable: Set<AudioProviderOrderItem> =
        AudioProviderOrderItem.entries.filter { it.isDisableable() }.toSet()

    fun serialize(providers: List<AudioProviderOrderItem>): String =
        normalize(providers).joinToString(",") { it.name }

    fun deserialize(value: String?): List<AudioProviderOrderItem> =
        normalize(
            value
                ?.split(',')
                ?.mapNotNull { raw -> AudioProviderOrderItem.entries.find { it.name.equals(raw.trim(), ignoreCase = true) } }
                .orEmpty(),
        )

    fun serializeDisabled(disabled: Set<AudioProviderOrderItem>): String =
        disabled
            .filter { it.isDisableable() }
            .joinToString(",") { it.name }

    fun deserializeDisabled(value: String?): Set<AudioProviderOrderItem> =
        value
            ?.split(',')
            ?.mapNotNull { raw -> AudioProviderOrderItem.entries.find { it.name.equals(raw.trim(), ignoreCase = true) } }
            ?.filter { it.isDisableable() }
            .orEmpty()
            .toSet()

    private fun normalize(providers: List<AudioProviderOrderItem>): List<AudioProviderOrderItem> =
        (providers + Default)
            .filter { it.isPlaybackProvider() }
            .distinct()
}
