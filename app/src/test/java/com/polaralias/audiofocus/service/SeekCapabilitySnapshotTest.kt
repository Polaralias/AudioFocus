package com.polaralias.audiofocus.service

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekCapabilitySnapshotTest {

    @Test
    fun assumesSeekToWhenCommanderPresentWithoutActions() {
        val snapshot = resolveSeekCapabilities(
            actions = 0L,
            hasCommander = true,
            hasPlayback = true
        )

        assertTrue(snapshot.supportsSeekTo)
        assertFalse(snapshot.canSeekRelativeOnly)
        assertTrue(snapshot.canSeekBy)
    }

    @Test
    fun detectsRelativeOnlySeekWhenSeekToNotAdvertised() {
        val snapshot = resolveSeekCapabilities(
            actions = PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND,
            hasCommander = true,
            hasPlayback = true
        )

        assertTrue(snapshot.supportsSeekTo)
        assertTrue(snapshot.canSeekRelativeOnly)
        assertTrue(snapshot.canSeekBy)
    }

    @Test
    fun reportsNoSeekSupportWhenCommanderMissingAndNoActions() {
        val snapshot = resolveSeekCapabilities(
            actions = 0L,
            hasCommander = false,
            hasPlayback = true
        )

        assertFalse(snapshot.supportsSeekTo)
        assertFalse(snapshot.canSeekBy)
        assertFalse(snapshot.canSeekRelativeOnly)
    }
}
