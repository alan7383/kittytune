package com.alananasss.kittytune.data.musicimport

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.Base64

/**
 * Payload delivered via the `data64` deep-link parameter when the musicapi
 * connection flow completes: soundcloud://musicapi/auth?data64=...
 *
 * Mirrors com.soundcloud.android.playlistimport.musicapi.data.apis.MusicApiAuth
 * from the official app.
 */
data class MusicApiAuth(
    @SerializedName("integrationUserUUID") val integrationUserUUID: String,
    @SerializedName("authModel") val authModel: Model?,
    @SerializedName("integration") val integration: Integration?
) {
    companion object {
        fun fromData64(data64: String): MusicApiAuth? = try {
            val json = String(Base64.getUrlDecoder().decode(data64), Charsets.UTF_8)
            Gson().fromJson(json, MusicApiAuth::class.java)
        } catch (e: Exception) {
            null
        }
    }

    data class Model(
        @SerializedName("uuid") val uuid: String?,
        @SerializedName("status") val status: String?,
        @SerializedName("error") val error: String?,
        @SerializedName("errorType") val errorType: String?
    )

    data class Integration(
        @SerializedName("type") val type: String?,
        @SerializedName("returnUrl") val returnUrl: String?,
        @SerializedName("isSuperPrivate") val isSuperPrivate: Boolean?
    )
}
