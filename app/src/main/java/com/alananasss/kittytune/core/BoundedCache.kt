package com.alananasss.kittytune.core

/**
 * Small fixed-capacity caches, for the ones that are keyed by track (issue #33).
 *
 * A map keyed by track id and never emptied is a leak with a slow fuse: it costs nothing over an
 * evening and holds an entry for every track scrolled past over a week-long session.
 *
 * The desktop app's copy of this class, kept identical: the two sides learned the same lesson
 * about caches keyed by track id, and the behaviour is easy to keep in step.
 *
 * Least-recently-used rather than oldest-first, because these all answer "what did we learn about
 * the track on screen": the entries worth keeping are the ones still being asked about.
 */
class BoundedCache<K, V>(private val maxEntries: Int) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
    }

    // accessOrder = true makes a get() count as use, which is what "recently used" has to mean here.
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxEntries
    }

    val size: Int get() = synchronized(map) { map.size }

    operator fun get(key: K): V? = synchronized(map) { map[key] }

    operator fun set(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }

    fun remove(key: K): V? = synchronized(map) { map.remove(key) }

    fun containsKey(key: K): Boolean = synchronized(map) { map.containsKey(key) }

    fun clear() {
        synchronized(map) { map.clear() }
    }
}
