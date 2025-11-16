package com.polaralias.audiofocus.service

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekCapabilitySnapshotTest {

    @Test
    fun doesNotAssumeSeekSupportWithoutActions() {
        val snapshot = resolveSeekCapabilities(actions = 0L)

        assertFalse(snapshot.supportsSeekTo)
        assertFalse(snapshot.canSeekBy)
        assertFalse(snapshot.canSeekRelativeOnly)
    }

    @Test
    fun detectsSeekToWhenActionAdvertised() {
        val snapshot = resolveSeekCapabilities(actions = PlaybackState.ACTION_SEEK_TO)

        assertTrue(snapshot.supportsSeekTo)
        assertTrue(snapshot.canSeekBy)
        assertFalse(snapshot.canSeekRelativeOnly)
    }

    @Test
    fun detectsRelativeOnlySeekWhenSeekToNotAdvertised() {
        val snapshot = resolveSeekCapabilities(
            actions = PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        )

        assertFalse(snapshot.supportsSeekTo)
        assertTrue(snapshot.canSeekRelativeOnly)
        assertTrue(snapshot.canSeekBy)
    }

    @Test
    fun treatsSeekForwardBackwardAsSeekToIndicators() {
        val snapshot = resolveSeekCapabilities(actions = ACTION_SEEK_FORWARD)

        assertTrue(snapshot.supportsSeekTo)
        assertTrue(snapshot.supportsRelativeSeek)
        assertFalse(snapshot.canSeekRelativeOnly)
    }
}
