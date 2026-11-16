package com.alananasss.kittytune.wear.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** An application acknowledgement, sent after the selected player accepted the command. */
@Serializable
data class WearCommandResult(
    val state: WearPlaybackState? = null,
    val error: String? = null,
) {
    fun encode(): ByteArray = WearCodecs.json.encodeToString(this).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): WearCommandResult =
            WearCodecs.json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }
}
