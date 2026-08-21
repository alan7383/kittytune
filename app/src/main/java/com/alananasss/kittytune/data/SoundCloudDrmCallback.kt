    package com.alananasss.kittytune.data

    import android.util.Log
    import androidx.media3.exoplayer.drm.ExoMediaDrm
    import androidx.media3.exoplayer.drm.MediaDrmCallback
    import okhttp3.MediaType.Companion.toMediaType
    import okhttp3.OkHttpClient
    import okhttp3.Request
    import okhttp3.RequestBody.Companion.toRequestBody
    import java.io.IOException
    import java.util.UUID

    /**
     * Custom MediaDrmCallback for SoundCloud CENC/Widevine DRM.
     *
     * Replicated from the official SoundCloud app's `SoundCloudMediaDrmCallback`.
     *
     * Flow:
     * 1. When ExoPlayer encounters a CENC-encrypted HLS stream, it requests a Widevine license
     * 2. This callback uses the `licenseAuthToken` (JWT obtained from the stream URL response)
     *    to build the Widevine license URL
     * 3. POSTs the key request data to the Widevine license server
     * 4. Returns the license key bytes to ExoPlayer for decryption
     */
    class SoundCloudDrmCallback(
        private val licenseAuthToken: String,
        private val isOffline: Boolean = false
    ) : MediaDrmCallback {

        companion object {
            private const val TAG = "SoundCloudDrmCallback"
            private const val WIDEVINE_PLAYBACK_URL =
                "https://license.media-streaming.soundcloud.cloud/playback/widevine?license_token="
            private const val WIDEVINE_PERSISTABLE_URL =
                "https://license.media-streaming.soundcloud.cloud/persistable/widevine?license_token="
            private val OCTET_STREAM = "application/octet-stream".toMediaType()
        }

        private val client: OkHttpClient
            get() = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()

        override fun executeKeyRequest(
            uuid: UUID,
            request: ExoMediaDrm.KeyRequest
        ): MediaDrmCallback.Response {
            Log.d(TAG, "executeKeyRequest — acquiring Widevine license (isOffline=$isOffline)")

            val baseUrl = if (isOffline) WIDEVINE_PERSISTABLE_URL else WIDEVINE_PLAYBACK_URL
            val licenseUrl = baseUrl + licenseAuthToken

            val requestBody = request.data.toRequestBody(OCTET_STREAM)

            val httpRequest = Request.Builder()
                .url(licenseUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "License request failed (code=${resp.code})")
                    throw IOException("License request failed (code=${resp.code})")
                }

                val body = resp.body?.bytes()
                    ?: throw IOException("Empty license response body")

                Log.d(TAG, "Widevine license acquired successfully (${body.size} bytes)")
                return MediaDrmCallback.Response(body)
            }
        }

        override fun executeProvisionRequest(
            uuid: UUID,
            request: ExoMediaDrm.ProvisionRequest
        ): MediaDrmCallback.Response {
            Log.d(TAG, "executeProvisionRequest")

            val provisionUrl = request.defaultUrl +
                    "&signedRequest=" + String(request.data)

            val requestBody = ByteArray(0).toRequestBody(OCTET_STREAM)

            val httpRequest = Request.Builder()
                .url(provisionUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Provision request failed (code=${resp.code})")
                    throw IOException("Provision request failed (code=${resp.code})")
                }

                val bytes = resp.body?.bytes()
                    ?: throw IOException("Empty provision response body")

                return MediaDrmCallback.Response(bytes)
            }
        }
    }
