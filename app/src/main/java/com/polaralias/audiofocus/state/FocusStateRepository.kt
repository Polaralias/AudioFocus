package com.polaralias.audiofocus.state

import com.polaralias.audiofocus.overlay.OverlayCommand
import com.polaralias.audiofocus.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class FocusStateRepository(
    private val overlayManager: OverlayManager,
    coroutineScope: CoroutineScope? = null,
) {
    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val windowState = MutableStateFlow<WindowSnapshot?>(null)
    private val playbackState = MutableStateFlow<PlaybackSnapshot?>(null)
    private val manualPause = MutableStateFlow(false)

    private val _overlayCommands = MutableStateFlow<OverlayCommand>(OverlayCommand.Hide)
    val overlayCommands: StateFlow<OverlayCommand> = _overlayCommands.asStateFlow()

    val manualPauseFlow: StateFlow<Boolean> = manualPause.asStateFlow()

    init {
        combine(windowState, playbackState, manualPause) { window, playback, pause ->
            overlayManager.evaluate(window, playback, pause)
        }
            .distinctUntilChanged()
            .onEach { _overlayCommands.value = it }
            .launchIn(scope)
    }

    fun updateWindowState(snapshot: WindowSnapshot?) {
        windowState.value = snapshot
    }

    fun updatePlaybackState(snapshot: PlaybackSnapshot?) {
        playbackState.value = snapshot
    }

    fun setManualPause(pause: Boolean) {
        manualPause.value = pause
    }

    fun toggleManualPause() {
        manualPause.update { !it }
    }
}
