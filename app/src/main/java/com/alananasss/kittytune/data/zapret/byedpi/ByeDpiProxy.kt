package com.alananasss.kittytune.data.zapret.byedpi

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ByeDpiProxy {
    companion object {
        private const val TAG = "ByeDpiProxy"
        init {
            try {
                System.loadLibrary("byedpi")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libbyedpi.so", e)
            }
        }
    }

    private val mutex = Mutex()
    private var proxyJob: Job? = null

    @Volatile
    private var fd = -1

    @Volatile
    var boundPort: Int = -1
        private set

    val isRunning: Boolean
        get() = fd >= 0

    suspend fun start(args: Array<String>, port: Int = -1): Int = mutex.withLock {
        if (fd >= 0) {
            Log.w(TAG, "Proxy is already running on port $boundPort (fd=$fd)")
            return 0
        }
        val fullArgs = if (args.firstOrNull() == "ciadpi") args else arrayOf("ciadpi") + args
        Log.i(TAG, "Starting proxy with args: ${fullArgs.joinToString(" ")}")
        val socketFd = jniCreateSocketWithCommandLine(fullArgs)
        if (socketFd < 0) {
            Log.e(TAG, "Failed to create socket with command line (returned $socketFd)")
            return -1
        }
        fd = socketFd
        boundPort = port
        proxyJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Proxy event loop started (fd=$socketFd)")
            val code = jniStartProxy(socketFd)
            Log.i(TAG, "Proxy event loop exited with code $code")
            mutex.withLock {
                if (fd == socketFd) {
                    fd = -1
                    boundPort = -1
                }
            }
        }
        return 0
    }

    suspend fun stop(): Int = mutex.withLock {
        val currentFd = fd
        if (currentFd < 0) return 0
        Log.i(TAG, "Stopping proxy (fd=$currentFd)")
        val res = jniStopProxy(currentFd)
        fd = -1
        boundPort = -1
        proxyJob?.cancel()
        proxyJob = null
        res
    }

    private external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    private external fun jniStartProxy(fd: Int): Int
    private external fun jniStopProxy(fd: Int): Int
}
