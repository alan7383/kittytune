package com.alananasss.kittytune.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import com.alananasss.kittytune.ui.player.audio.FxDeck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves the playing track as an audio file with the sound the listener hears: every effect, the
 * equalizer, the speed and the pitch.
 *
 * Rendered offline by Media3's Transformer through a fresh [FxDeck], configured the way the player
 * configures its own, then Sonic for speed and pitch, encoded to AAC and saved in Music/KittyTune Effects.
 * One export at a time, owned by the app: leaving the sheet does not stop it.
 */
@OptIn(UnstableApi::class)
object AudioFxExporter {

    private const val TAG = "AudioFxExporter"
    private const val FOLDER = "KittyTune Effects"
    const val BITRATE = 256_000

    enum class Failure { PROTECTED, UNAVAILABLE, WRITE, OTHER }

    sealed interface State {
        data object Idle : State
        data class Running(val title: String, val progress: Float) : State
        data class Done(val uri: Uri, val name: String) : State
        data class Failed(val failure: Failure) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var transformer: Transformer? = null

    /** True while the export sheet is on screen; a finished export then needs no toast. */
    @Volatile var sheetOpen = false

    /** "Artist - Title (FX)", stripped of what a file name cannot hold. */
    fun suggestedName(track: Track): String {
        val base = listOf(track.displayArtist, track.title.orEmpty()).filter { it.isNotBlank() }.joinToString(" - ")
        return sanitize("${base.ifBlank { "KittyTune" }} (FX)")
    }

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(120).ifBlank { "KittyTune (FX)" }

    fun dismiss() {
        if (_state.value !is State.Running) _state.value = State.Idle
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
        job?.cancel()
        _state.value = State.Idle
    }

    /** True when what plays for [track] is a protected (DRM) stream: those are played, never copied. */
    fun isProtected(track: Track): Boolean {
        if (MusicManager.getDrmToken(track.id) != null) return true
        val item = MusicManager.player.currentMediaItem
        return item?.mediaId == track.id.toString() && item.localConfiguration?.drmConfiguration != null
    }

    fun export(context: Context, track: Track, effects: AudioEffectsState, equalizer: EqualizerState, fileName: String) {
        if (_state.value is State.Running) return
        val app = context.applicationContext
        if (isProtected(track)) {
            _state.value = State.Failed(Failure.PROTECTED)
            return
        }
        val source = sourceItem(track)
        val dataSource = MusicManager.exportDataSourceFactory
        if (source == null || dataSource == null) {
            _state.value = State.Failed(Failure.UNAVAILABLE)
            return
        }
        _state.value = State.Running(track.title.orEmpty(), 0f)
        job = scope.launch {
            val output = File(app.cacheDir, "fx-export/${System.nanoTime()}.m4a").apply { parentFile?.mkdirs() }
            try {
                val error = render(app, source, dataSource, effects, equalizer, output) { progress ->
                    _state.value = State.Running(track.title.orEmpty(), progress * 0.97f)
                }
                if (error != null) {
                    Log.w(TAG, "export failed", error)
                    _state.value = State.Failed(
                        when (error.errorCode) {
                            ExportException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            ExportException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                            ExportException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND -> Failure.UNAVAILABLE
                            else -> Failure.OTHER
                        },
                    )
                    return@launch
                }
                val name = "${sanitize(fileName)}.m4a"
                val saved = withContext(Dispatchers.IO) { publish(app, output, name, track) }
                _state.value = if (saved != null) State.Done(saved, name) else State.Failed(Failure.WRITE)
            } catch (e: kotlinx.coroutines.CancellationException) {
                transformer?.cancel()
                transformer = null
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "export failed", e)
                _state.value = State.Failed(Failure.OTHER)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { output.delete() }
            }
        }
    }

    /**
     * Renders [source] into [output] (AAC in MP4) through a fresh [FxDeck] configured like the player's,
     * then Sonic for speed and pitch. Must run on a thread with a Looper (the main one). Returns the
     * failure, or null once the file is complete. [onProgress] gets 0..1.
     */
    internal suspend fun render(
        context: Context,
        source: MediaItem,
        dataSource: androidx.media3.datasource.DataSource.Factory,
        effects: AudioEffectsState,
        equalizer: EqualizerState,
        output: File,
        onProgress: (Float) -> Unit = {},
    ): ExportException? {
        val deck = FxDeck().apply {
            applyEffects(effects)
            applyEqualizer(equalizer)
        }
        val sonic = SonicAudioProcessor().apply {
            setSpeed(effects.speed)
            setPitch(if (effects.isPitchEnabled) effects.speed else 1f)
        }
        val processors: List<AudioProcessor> = deck.processors() + sonic
        val edited = EditedMediaItem.Builder(source)
            .setRemoveVideo(true)
            .setEffects(Effects(processors, emptyList()))
            .build()

        val result = kotlinx.coroutines.CompletableDeferred<ExportException?>()
        val built = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                    .setRequestedAudioEncoderSettings(
                        androidx.media3.transformer.AudioEncoderSettings.Builder().setBitrate(BITRATE).build(),
                    )
                    .build(),
            )
            .setAssetLoaderFactory(
                DefaultAssetLoaderFactory(
                    context,
                    androidx.media3.transformer.DefaultDecoderFactory.Builder(context).build(),
                    androidx.media3.common.util.Clock.DEFAULT,
                    DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource),
                    androidx.media3.datasource.DataSourceBitmapLoader(context),
                ),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    result.complete(null)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    result.complete(exportException)
                }
            })
            .build()
        transformer = built
        try {
            built.start(edited, output.absolutePath)
            val holder = ProgressHolder()
            while (!result.isCompleted) {
                if (built.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                delay(150)
            }
            return result.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            built.cancel()
            throw e
        } finally {
            transformer = null
        }
    }

    /** What the player has loaded for [track], without its DRM or clipping: the source as such. */
    private fun sourceItem(track: Track): MediaItem? {
        val playing = MusicManager.player.currentMediaItem?.takeIf { it.mediaId == track.id.toString() }
        val config = playing?.localConfiguration
        val uri = config?.uri ?: Uri.parse("soundtune://track/${track.id}")
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id.toString())
            .apply { config?.mimeType?.let(::setMimeType) }
            .build()
    }

    /** Copies the rendered file into the shared Music folder and returns a URI other apps can open. */
    private fun publish(context: Context, file: File, name: String, track: Track): Uri? = runCatching {
        if (!file.isFile || file.length() == 0L) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
                put(MediaStore.Audio.Media.TITLE, track.title.orEmpty())
                put(MediaStore.Audio.Media.ARTIST, track.displayArtist)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Before scoped storage, the app's own Music folder needs no permission and is still browsable.
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), FOLDER).apply { mkdirs() }
            var target = File(dir, name)
            var n = 2
            while (target.exists()) target = File(dir, name.removeSuffix(".m4a") + " ($n).m4a").also { n++ }
            file.copyTo(target)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("audio/mp4"), null)
            FileProvider.getUriForFile(context, "${context.packageName}.provider", target)
        }
    }.onFailure { Log.w(TAG, "could not save the export", it) }.getOrNull()

    fun openIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "audio/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(uri: Uri): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        null,
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
