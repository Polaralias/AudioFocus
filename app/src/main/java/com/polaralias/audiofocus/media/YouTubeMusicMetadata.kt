package com.polaralias.audiofocus.media

import android.media.MediaMetadata

object YouTubeMusicMetadata {
    const val METADATA_KEY_VIDEO_WIDTH = "android.media.metadata.VIDEO_WIDTH"
    const val METADATA_KEY_VIDEO_HEIGHT = "android.media.metadata.VIDEO_HEIGHT"
    const val METADATA_KEY_PRESENTATION_DISPLAY_TYPE = "android.media.metadata.PRESENTATION_DISPLAY_TYPE"
    const val PRESENTATION_DISPLAY_TYPE_VIDEO = 1L

    data class VideoMetadata(
        val videoWidth: Long,
        val videoHeight: Long,
        val presentationDisplayType: Long,
    ) {
        val indicatesVideo: Boolean
            get() = (videoWidth > 0 && videoHeight > 0) ||
                presentationDisplayType == PRESENTATION_DISPLAY_TYPE_VIDEO
    }

    fun extractVideoMetadata(metadata: MediaMetadata?): VideoMetadata? {
        if (metadata == null) {
            return null
        }

        val hasVideoSignal = metadata.containsKey(METADATA_KEY_VIDEO_WIDTH) ||
            metadata.containsKey(METADATA_KEY_VIDEO_HEIGHT) ||
            metadata.containsKey(METADATA_KEY_PRESENTATION_DISPLAY_TYPE)

        if (!hasVideoSignal) {
            return null
        }

        @Suppress("WrongConstant")
        val width = metadata.getLong(METADATA_KEY_VIDEO_WIDTH)
        @Suppress("WrongConstant")
        val height = metadata.getLong(METADATA_KEY_VIDEO_HEIGHT)
        @Suppress("WrongConstant")
        val presentationType = metadata.getLong(METADATA_KEY_PRESENTATION_DISPLAY_TYPE)

        return VideoMetadata(
            videoWidth = width,
            videoHeight = height,
            presentationDisplayType = presentationType,
        )
    }
}
