package com.polaralias.audiofocus.window

data class WindowCacheTelemetry(
    val totalStores: Long = 0,
    val totalHits: Long = 0,
    val totalClears: Long = 0,
    val totalMisses: Long = 0,
    val lastAction: CacheAction = CacheAction.NONE,
    val lastReason: CacheReason = CacheReason.NONE,
    val timestamp: Long = 0L,
)

enum class CacheAction {
    NONE,
    STORED,
    RETURNED,
    CLEARED,
    MISS,
}

enum class CacheReason {
    NONE,
    VISIBLE_SNAPSHOT,
    TRANSIENT_SYSTEM,
    NO_WINDOWS,
    NO_SUPPORTED_WINDOWS,
    BACKGROUND,
    EXPIRED,
}
