    package com.alananasss.kittytune.data

    /**
     * Holds the resolved stream URL together with optional Widevine DRM license token.
     * CENC-encrypted HLS streams on SoundCloud return a `licenseAuthToken` JWT
     * that must be exchanged for a Widevine license before playback can start.
     */
    data class ResolvedStream(
        val url: String,
        val licenseAuthToken: String? = null
    ) {
        /** true when the stream requires Widevine DRM decryption */
        val isDrmProtected: Boolean get() = !licenseAuthToken.isNullOrEmpty()
    }
