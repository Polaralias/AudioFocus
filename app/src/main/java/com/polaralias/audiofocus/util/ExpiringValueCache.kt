package com.polaralias.audiofocus.util

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

class ExpiringValueCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    init {
        require(ttlMillis > 0) { "ttlMillis must be > 0" }
    }

    private data class Entry<V : Any>(val value: V, val expiry: Long)

    private val entries = ConcurrentHashMap<K, Entry<V>>()

    fun put(key: K, value: V) {
        entries[key] = Entry(value, clock() + ttlMillis)
    }

    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiry <= clock()) {
            entries.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun getOrPut(key: K, provider: () -> V): V {
        val cached = get(key)
        if (cached != null) {
            return cached
        }
        val value = provider()
        put(key, value)
        return value
    }

    fun remove(key: K) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }
}
