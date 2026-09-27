package com.alananasss.kittytune.utils

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import androidx.media3.common.Player
import com.alananasss.kittytune.BuildConfig
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogManager {

    private const val CRASH_FILE_NAME = "last_crash.log"
    private var isInitialized = false

    private val SENSITIVE_PATTERNS = listOf(
        Regex("(?i)(authorization\\s*:\\s*bearer\\s+)[a-zA-Z0-9_.-]+"),
        Regex("(?i)(client_secret=)[a-zA-Z0-9_.-]+"),
        Regex("(?i)(oauth_token=)[a-zA-Z0-9_.-]+"),
        Regex("(?i)(cookie\\s*:\\s*)[^\\r\\n]+"),
        Regex("(?i)(token=)[a-zA-Z0-9_.-]+"),
        Regex("(?i)(password=)[^&\\s\\r\\n]+")
    )

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(context, thread, throwable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        val stackTrace = stringWriter.toString()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val diagnostics = runCatching { getDiagnosticsSummary(context) }.getOrDefault("")

        val content = buildString {
            appendLine("=== UNCAUGHT EXCEPTION CRASH REPORT ===")
            appendLine("Timestamp: $timestamp")
            @Suppress("DEPRECATION")
            appendLine("Thread: ${thread.name} (id: ${thread.id})")
            appendLine("Exception: ${throwable::class.java.name}")
            appendLine("Message: ${throwable.message ?: "None"}")
            appendLine()
            appendLine("----------------- STACKTRACE -----------------")
            appendLine(stackTrace)
            appendLine("---------------- SYSTEM STATE ----------------")
            appendLine(diagnostics)
            appendLine("==============================================")
        }
        file.writeText(content)
    }

    fun getLastCrash(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        return if (file.exists()) {
            runCatching { file.readText() }.getOrNull()
        } else null
    }

    fun clearLastCrash(context: Context) {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

    fun getDiagnosticsMap(context: Context): Map<String, List<Pair<String, String>>> {
        val prefs = PlayerPreferences(context)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val maxHeap = runtime.maxMemory()
        val totalHeap = runtime.totalMemory()
        val freeHeap = runtime.freeMemory()
        val usedHeap = totalHeap - freeHeap

        val version = AppUtils.getAppVersion(context)
        val versionCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
            }
        } catch (e: Exception) { -1L }

        val currentTrack = MusicManager.currentTrack
        val isPlaying = MusicManager.player.isPlaying
        val playbackState = when (MusicManager.player.playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN"
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val netType = when {
            caps == null -> "None / Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }

        val cacheSizeBytes = runCatching {
            context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }.getOrDefault(0L)

        return mapOf(
            context.getString(R.string.logs_sec_device) to listOf(
                "Model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "Device / Brand" to "${Build.DEVICE} / ${Build.BRAND}",
                "Product / Hardware" to "${Build.PRODUCT} / ${Build.HARDWARE}",
                "Android OS" to "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                "Security Patch" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"),
                "Display / Build ID" to Build.DISPLAY,
                "Supported ABIs" to Build.SUPPORTED_ABIS.joinToString(", ")
            ),
            context.getString(R.string.logs_sec_app) to listOf(
                "Version" to "$version ($versionCode)",
                "Package" to context.packageName,
                "Build Type" to "${BuildConfig.BUILD_TYPE} (Debug: ${BuildConfig.DEBUG})",
                "Language" to "${prefs.getAppLanguage()} (System: ${LocaleUtils.getLocale(context)})",
                "Theme Mode" to "${prefs.getThemeMode()} (Dynamic: ${prefs.getDynamicTheme()}, PureBlack: ${prefs.getPureBlack()})"
            ),
            context.getString(R.string.logs_sec_memory) to listOf(
                "Total RAM" to formatBytes(memInfo.totalMem),
                "Available RAM" to formatBytes(memInfo.availMem),
                "Low Memory Alert" to if (memInfo.lowMemory) "YES (Warning)" else "Normal",
                "JVM Heap Used" to "${formatBytes(usedHeap)} / ${formatBytes(totalHeap)} (Max: ${formatBytes(maxHeap)})",
                "Internal Cache" to formatBytes(cacheSizeBytes),
                "Downloaded Tracks" to "${DownloadManager.downloadedIds.value.size}"
            ),
            context.getString(R.string.logs_sec_playback) to listOf(
                "Player State" to "$playbackState (Playing: $isPlaying)",
                "Current Track" to (currentTrack?.let { "${it.title} - ${it.displayArtist} [id: ${it.id}]" } ?: "None"),
                "Track Duration / Pos" to (currentTrack?.let { "${it.durationMs}ms / ${MusicManager.player.currentPosition}ms" } ?: "N/A"),
                "Crossfade" to if (prefs.getCrossfadeEnabled()) "Enabled (${prefs.getCrossfadeDuration()}s)" else "Disabled"
            ),
            context.getString(R.string.logs_sec_network) to listOf(
                "Connection Type" to netType,
                "Proxy Enabled" to "${prefs.getProxyEnabled()} (${prefs.getProxyHost()}:${prefs.getProxyPort()})",
                "Audio Providers" to prefs.getAudioProviderOrder().joinToString(", ") { it.name },
                "Tidal Quality" to prefs.getTidalAudioQuality().name,
                "Deezer Quality" to prefs.getDeezerAudioQuality().name
            )
        )
    }

    fun getDiagnosticsSummary(context: Context): String {
        val map = getDiagnosticsMap(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        return buildString {
            appendLine("=== KITTYTUNE DIAGNOSTICS & SYSTEM REPORT ===")
            appendLine("Generated At: $timestamp")
            appendLine()
            for ((section, items) in map) {
                appendLine("[$section]")
                for ((key, value) in items) {
                    appendLine("  $key: $value")
                }
                appendLine()
            }
            appendLine("=============================================")
        }
    }

    suspend fun getLogcat(maxLines: Int = 1200): String = withContext(Dispatchers.IO) {
        val pid = Process.myPid()
        val lines = mutableListOf<String>()

        fun readProcess(process: java.lang.Process) {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                lines.add(sanitizeLogLine(line))
                line = reader.readLine()
            }
            reader.close()
            process.destroy()
        }

        try {
            // First try with --pid filter (cleanest and fastest)
            val p1 = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "--pid=$pid"))
            readProcess(p1)
        } catch (_: Exception) {}

        if (lines.isEmpty()) {
            try {
                // Fallback: standard logcat dump filtered in-memory
                val p2 = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
                val pidString = "($pid)"
                val pidColon = "$pid:"
                val reader = BufferedReader(InputStreamReader(p2.inputStream))
                var line = reader.readLine()
                while (line != null) {
                    if (line.contains(pidString) || line.contains(pidColon) || line.contains("KittyTune") || line.contains("ExoPlayer")) {
                        lines.add(sanitizeLogLine(line))
                    }
                    line = reader.readLine()
                }
                reader.close()
                p2.destroy()
            } catch (e: Exception) {
                return@withContext "Error reading logcat: ${e.message}"
            }
        }

        if (lines.isEmpty()) {
            "No logcat entries available for process PID $pid."
        } else if (lines.size > maxLines) {
            lines.takeLast(maxLines).joinToString("\n")
        } else {
            lines.joinToString("\n")
        }
    }

    suspend fun getFullReport(context: Context): String = withContext(Dispatchers.IO) {
        val diagnostics = getDiagnosticsSummary(context)
        val crash = getLastCrash(context)
        val logcat = getLogcat()

        buildString {
            append(diagnostics)
            appendLine()
            if (!crash.isNullOrBlank()) {
                appendLine("=== LAST RECORDED CRASH ===")
                appendLine(crash)
                appendLine("============================")
                appendLine()
            }
            appendLine("=== RECENT PROCESS LOGCAT ===")
            appendLine(logcat)
            appendLine("=== END OF LOG REPORT ===")
        }
    }

    fun sanitizeLogLine(line: String): String {
        var sanitized = line
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = pattern.replace(sanitized) { matchResult ->
                val prefix = matchResult.groupValues.getOrNull(1) ?: ""
                "$prefix[REDACTED]"
            }
        }
        return sanitized
    }

    fun copyToClipboard(context: Context, text: String, label: String = "KittyTune Logs") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun shareText(context: Context, text: String, title: String = "KittyTune Logs") {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooserIntent = Intent.createChooser(sendIntent, title).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooserIntent)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}
