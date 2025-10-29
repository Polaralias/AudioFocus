package com.polaralias.audiofocus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiringValueCacheTest {
    @Test
    fun `getOrPut caches value until expiry`() {
        var time = 0L
        val cache = ExpiringValueCache<String, String>(ttlMillis = 1_000L) { time }

        val initial = cache.getOrPut("key") { "value" }
        assertEquals("value", initial)

        time = 500L
        assertEquals("value", cache.get("key"))

        time = 999L
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `expired entries are cleared on access`() {
        var time = 0L
        val cache = ExpiringValueCache<String, String>(ttlMillis = 1_000L) { time }

        cache.put("key", "value")
        time = 1_200L

        assertNull(cache.get("key"))
    }

    @Test
    fun `remove and clear invalidate cache`() {
        var time = 0L
        val cache = ExpiringValueCache<String, String>(ttlMillis = 1_000L) { time }

        cache.put("a", "one")
        cache.put("b", "two")

        cache.remove("a")
        assertNull(cache.get("a"))
        assertEquals("two", cache.get("b"))

        cache.clear()
        assertNull(cache.get("b"))
    }
}
