package com.polaralias.audiofocus.overlay

enum class App { YOUTUBE, YTMUSIC, OTHER }
enum class WindowState { FULLSCREEN, MINIMIZED_IN_APP, PIP, BACKGROUND, NON_FULLSCREEN_VIDEO }
enum class Playback { PLAYING_VIDEO_VISIBLE, PLAYING_BACKGROUND, PAUSED, STOPPED }
enum class OverlayBehaviour { FULL_SCREEN_OVERLAY, PARTIAL_80_PASS_THROUGH, NONE }

data class ContextState(
    val app: App,
    val windowState: WindowState,
    val playback: Playback
)

object OverlayPolicy {
    fun decideOverlay(state: ContextState): OverlayBehaviour = when (state.app) {
        App.YOUTUBE -> decideForYouTube(state)
        App.YTMUSIC -> decideForYouTubeMusic(state)
        App.OTHER -> OverlayBehaviour.NONE
    }

    private fun decideForYouTube(state: ContextState): OverlayBehaviour {
        return when (state.playback) {
            Playback.PAUSED, Playback.STOPPED -> OverlayBehaviour.NONE
            Playback.PLAYING_BACKGROUND -> OverlayBehaviour.NONE
            Playback.PLAYING_VIDEO_VISIBLE -> when (state.windowState) {
                WindowState.FULLSCREEN, WindowState.MINIMIZED_IN_APP, WindowState.PIP ->
                    OverlayBehaviour.FULL_SCREEN_OVERLAY
                else -> OverlayBehaviour.NONE
            }
        }
    }

    private fun decideForYouTubeMusic(state: ContextState): OverlayBehaviour {
        if (state.windowState == WindowState.BACKGROUND) return OverlayBehaviour.NONE
        return when (state.playback) {
            Playback.PAUSED, Playback.STOPPED -> OverlayBehaviour.NONE
            Playback.PLAYING_BACKGROUND -> OverlayBehaviour.NONE
            Playback.PLAYING_VIDEO_VISIBLE -> when (state.windowState) {
                WindowState.FULLSCREEN -> OverlayBehaviour.FULL_SCREEN_OVERLAY
                WindowState.MINIMIZED_IN_APP,
                WindowState.NON_FULLSCREEN_VIDEO,
                WindowState.PIP -> OverlayBehaviour.PARTIAL_80_PASS_THROUGH
                else -> OverlayBehaviour.NONE
            }
        }
    }
}
