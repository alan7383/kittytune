package com.alananasss.kittytune.utils

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * helpers for detecting and validating animated GIF files
 *
 * detection always sniffs the actual content (magic bytes) and never trusts the
 * file extension: playlist covers are stored on disk with a .jpg name regardless
 * of their real format (see DownloadManager.updatePlaylistCover), and photo
 * picker URIs may not expose a reliable MIME type either
 */
object GifUtils {

    /** maximum size of an animated GIF cover we are willing to store 10 MB */
    const val MAX_GIF_COVER_BYTES: Long = 10L * 1024 * 1024
    const val MAX_GIF_COVER_MB: Int = 10

    /** every GIF variant starts with "GIF8" ("GIF87a" / "GIF89a"). */
    private const val GIF_HEADER_PREFIX = "GIF8"
    private const val GIF_HEADER_LENGTH = 6

    /** reads up to [GIF_HEADER_LENGTH] bytes from [input] without closing it */
    private fun readHeader(input: InputStream): String? = try {
        val header = ByteArray(GIF_HEADER_LENGTH)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            if (n < 0) break
            read += n
        }
        if (read == GIF_HEADER_LENGTH) {
            String(header, 0, GIF_HEADER_LENGTH, Charsets.US_ASCII)
        } else null
    } catch (_: Exception) {
        null
    }

    /** true if the content behind [uri] is a GIF (animated or not) */
    fun isGif(resolver: ContentResolver, uri: Uri): Boolean = try {
        resolver.openInputStream(uri)?.use { input ->
            readHeader(input)?.startsWith(GIF_HEADER_PREFIX) == true
        } ?: false
    } catch (_: Exception) {
        false
    }

    /** true if [file] exists and its content is a GIF */
    fun isGifFile(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() < GIF_HEADER_LENGTH) return false
        return try {
            FileInputStream(file).use { input ->
                readHeader(input)?.startsWith(GIF_HEADER_PREFIX) == true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * cheap size probe for a content [uri] without reading the whole stream
     * returns -1 when the size cannot be determined
     */
    fun contentSize(resolver: ContentResolver, uri: Uri): Long = try {
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    /** true when an animated GIF cover of this size may be saved */
    fun isAcceptableGifCoverSize(sizeBytes: Long): Boolean =
        sizeBytes in 1..MAX_GIF_COVER_BYTES
}
