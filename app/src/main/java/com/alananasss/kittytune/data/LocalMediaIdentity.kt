package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.LocalTrack
import java.io.File
import java.net.URI
import java.net.URLDecoder

/** Legacy scans used a negative 32-bit path hash and may lack a source field. */
internal val LocalTrack.isUserLocalMedia: Boolean
    get() = source == "local" || (
        source == "soundcloud" && id < 0 && localAudioPath.isNotBlank() &&
            id == -kotlin.math.abs(localAudioPath.hashCode().toLong())
        )

/** A SAF document keeps the same provider and document ID under different tree grants. */
internal fun localMediaIdentity(path: String): String? {
    if (path.isBlank()) return null
    return runCatching {
        when {
            path.startsWith("content://") -> {
                val uri = URI(path)
                val authority = uri.authority ?: return null
                val rawPath = uri.rawPath ?: return null
                val document = when {
                    "/document/" in rawPath -> rawPath.substringAfter("/document/")
                    "/tree/" in rawPath -> rawPath.substringAfter("/tree/").substringBefore('/')
                    else -> return uri.toString()
                }
                val decoded = URLDecoder.decode(document.replace("+", "%2B"), Charsets.UTF_8.name()).trimEnd('/')
                "content://${authority.lowercase()}/$decoded"
            }
            path.startsWith("file://") -> File(URI(path)).canonicalPath
            !path.contains("://") -> File(path).canonicalPath
            else -> null
        }
    }.getOrNull()
}
