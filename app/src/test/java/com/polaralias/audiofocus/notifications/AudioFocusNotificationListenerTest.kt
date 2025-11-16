package com.polaralias.audiofocus.notifications

import android.media.MediaMetadata
import com.polaralias.audiofocus.media.YouTubeMusicMetadata
import com.polaralias.audiofocus.state.PlaybackContentType
import com.polaralias.audiofocus.util.ExpiringValueCache
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFocusNotificationListenerTest {
    @Test
    fun deriveYouTubeMusicContent_updatesCacheWhenVideoHintsChange() {
        val listener = AudioFocusNotificationListener()
        val deriveMethod = AudioFocusNotificationListener::class.java
            .getDeclaredMethod("deriveYouTubeMusicContent", MediaMetadata::class.java)
            .apply { isAccessible = true }

        val videoMetadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "track123")
            .putLong(YouTubeMusicMetadata.METADATA_KEY_VIDEO_WIDTH, 1920L)
            .putLong(YouTubeMusicMetadata.METADATA_KEY_VIDEO_HEIGHT, 1080L)
            .putLong(
                YouTubeMusicMetadata.METADATA_KEY_PRESENTATION_DISPLAY_TYPE,
                YouTubeMusicMetadata.PRESENTATION_DISPLAY_TYPE_VIDEO,
            )
            .build()

        val audioMetadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "track123")
            .build()

        val firstResult = deriveMethod.invoke(listener, videoMetadata) as PlaybackContentType
        assertEquals(PlaybackContentType.VIDEO, firstResult)

        val secondResult = deriveMethod.invoke(listener, audioMetadata) as PlaybackContentType
        assertEquals(PlaybackContentType.AUDIO_ONLY, secondResult)

        val cacheField = AudioFocusNotificationListener::class.java
            .getDeclaredField("contentTypeCache")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(listener) as ExpiringValueCache<String, PlaybackContentType>

        val cacheKeyMethod = AudioFocusNotificationListener::class.java
            .getDeclaredMethod(
                "buildYouTubeMusicCacheKey",
                String::class.java,
                YouTubeMusicMetadata.VideoMetadata::class.java,
            ).apply { isAccessible = true }

        val audioKey = cacheKeyMethod.invoke(
            listener,
            "track123",
            null,
        ) as String

        assertEquals(PlaybackContentType.AUDIO_ONLY, cache.get(audioKey))
    }
}
