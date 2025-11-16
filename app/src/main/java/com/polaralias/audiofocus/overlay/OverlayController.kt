package com.polaralias.audiofocus.overlay

class OverlayController(
    private val applier: OverlayApplier
) {
    private var lastBehaviour: OverlayBehaviour? = null

    fun update(state: ContextState) {
        val behaviour = OverlayPolicy.decideOverlay(state)
        if (behaviour == lastBehaviour) return
        lastBehaviour = behaviour
        when (behaviour) {
            OverlayBehaviour.FULL_SCREEN_OVERLAY -> applier.showFullScreenOverlay()
            OverlayBehaviour.NONE -> applier.hideOverlay()
        }
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val YTMUSIC_PACKAGE = "com.google.android.apps.youtube.music"

        fun mapPackageToApp(pkg: String?): App = when (pkg) {
            YOUTUBE_PACKAGE -> App.YOUTUBE
            YTMUSIC_PACKAGE -> App.YTMUSIC
            else -> App.OTHER
        }
    }
}
