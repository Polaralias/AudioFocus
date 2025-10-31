package com.polaralias.audiofocus.data

data class OverlayPreferences(
    val enableYouTube: Boolean = true,
    val enableYouTubeMusic: Boolean = true,
    val startOnBoot: Boolean = false,
    val dimAmount: Float = 0.9f
)
