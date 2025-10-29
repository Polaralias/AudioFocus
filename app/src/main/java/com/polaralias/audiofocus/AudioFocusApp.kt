package com.polaralias.audiofocus

import android.app.Application
import com.polaralias.audiofocus.overlay.OverlayManager
import com.polaralias.audiofocus.state.FocusStateRepository

class AudioFocusApp : Application() {
    val focusStateRepository: FocusStateRepository by lazy {
        FocusStateRepository(OverlayManager())
    }
}
