package com.alananasss.kittytune.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object DownloadFiles {
    fun name(trackId: Long, artist: String, title: String, extension: String): String {
        val clean = "$artist - $title".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim()
        val label = buildString {
            val points = clean.codePoints().iterator()
            var bytes = 0
            while (points.hasNext()) {
                val point = points.nextInt()
                val size = when {
                    point <= 0x7f -> 1
                    point <= 0x7ff -> 2
                    point <= 0xffff -> 3
                    else -> 4
                }
                if (bytes + size > 180) break
                appendCodePoint(point)
                bytes += size
            }
        }
        return "$label [$trackId].$extension"
    }

    fun store(source: File, directory: File, fileName: String): File {
        directory.mkdirs()
        val target = File(directory, fileName)
        val partial = File.createTempFile("kittytune-", ".part", directory)
        try {
            source.inputStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } finally {
            partial.delete()
        }
    }
}
