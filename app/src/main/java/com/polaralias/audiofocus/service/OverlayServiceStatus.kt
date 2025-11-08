package com.polaralias.audiofocus.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight status publisher so the UI can reflect what the overlay service is doing.
 */
object OverlayServiceStatusTracker {
    private val _status = MutableStateFlow(
        OverlayServiceStatus(state = OverlayServiceState.STOPPED)
    )
    val status: StateFlow<OverlayServiceStatus> = _status.asStateFlow()

    fun update(state: OverlayServiceState, detail: String? = null) {
        val newStatus = OverlayServiceStatus(state, detail)
        if (_status.value != newStatus) {
            _status.value = newStatus
        }
    }
}

data class OverlayServiceStatus(
    val state: OverlayServiceState,
    val detail: String? = null,
)

enum class OverlayServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    WAITING_FOR_MEDIA,
    ERROR
}
