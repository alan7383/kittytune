package com.alananasss.kittytune.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File

object PlayerArtworkCache {
    private val legacyName = Regex("""art_-?\d+\.jpg""")

    internal fun isLegacyArtwork(name: String): Boolean = legacyName.matches(name)

    private fun cacheFile(context: Context, trackId: Long): File =
        File(File(context.cacheDir, "player_art"), "art_$trackId.jpg")

    @Synchronized
    fun store(context: Context, trackId: Long, bitmap: Bitmap) {
        val file = cacheFile(context, trackId)
        if (file.length() > 0) return
        val directory = file.parentFile ?: return
        directory.mkdirs()
        val temp = File.createTempFile("cover-", ".tmp", directory)
        try {
            if (temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }) {
                temp.renameTo(file)
            }
        } finally {
            temp.delete()
        }
    }

    fun pathOrNull(context: Context, trackId: Long): String? =
        sequenceOf(cacheFile(context, trackId), File(context.filesDir, "art_$trackId.jpg"))
            .firstOrNull { it.isFile && it.length() > 0 }?.absolutePath
}
