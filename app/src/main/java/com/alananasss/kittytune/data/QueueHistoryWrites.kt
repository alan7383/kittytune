package com.alananasss.kittytune.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One encoder workspace, and FIFO ordering even when a database operation suspends. */
internal class QueueHistoryWrites(scope: CoroutineScope, onFailure: (Exception) -> Unit) {
    private val pending = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in pending) {
                try {
                    write()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    onFailure(failure)
                }
            }
        }
    }

    fun submit(write: suspend () -> Unit) {
        pending.trySend(write).getOrThrow()
    }
}
