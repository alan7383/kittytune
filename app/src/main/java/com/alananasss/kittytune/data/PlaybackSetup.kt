package com.alananasss.kittytune.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object PlaybackSetup {
    /** Background resolution already checked storage for these items. */
    object Prepared

    fun offlineKey(prepared: Boolean, embedded: ByteArray?, legacyLookup: () -> ByteArray?): ByteArray? =
        when {
            embedded != null -> embedded.copyOf()
            prepared -> null
            else -> legacyLookup()
        }
}

internal class DeferredPlaybackArtwork<T>(private val worker: CoroutineDispatcher = Dispatchers.IO) {
    private var job: Job? = null

    fun load(scope: CoroutineScope, item: T, fetch: suspend () -> ByteArray?, publish: (T, ByteArray) -> Unit): Job {
        job?.cancel()
        return scope.launch {
            try {
                val bytes = withContext(worker) { fetch() }
                ensureActive()
                if (bytes != null) publish(item, bytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Artwork is optional; the item's artworkUri is still available.
            }
        }.also { job = it }
    }
}
