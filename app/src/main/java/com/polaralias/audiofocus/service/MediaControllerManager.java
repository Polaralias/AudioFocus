package com.polaralias.audiofocus.service;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.text.TextUtils;

public class MediaControllerManager {
    private MediaController activeController;

    public void setActiveController(MediaController controller) {
        activeController = controller;
    }

    public void play() {
        MediaController controller = activeController;
        if (controller != null && controller.getTransportControls() != null) {
            controller.getTransportControls().play();
        }
    }

    public void pause() {
        MediaController controller = activeController;
        if (controller != null && controller.getTransportControls() != null) {
            controller.getTransportControls().pause();
        }
    }

    public void skipNext() {
        MediaController controller = activeController;
        if (controller != null && controller.getTransportControls() != null) {
            controller.getTransportControls().skipToNext();
        }
    }

    public void skipPrevious() {
        MediaController controller = activeController;
        if (controller != null && controller.getTransportControls() != null) {
            controller.getTransportControls().skipToPrevious();
        }
    }

    public void seekTo(long position) {
        MediaController controller = activeController;
        if (controller != null && controller.getTransportControls() != null) {
            controller.getTransportControls().seekTo(position);
        }
    }

    public void seekBy(long delta) {
        MediaController controller = activeController;
        if (controller != null && controller.getTransportControls() != null) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                long newPos = state.getPosition() + delta;
                if (newPos < 0) newPos = 0;

                long duration = getDuration();
                if (duration > 0 && newPos > duration) newPos = duration;

                controller.getTransportControls().seekTo(newPos);
            }
        }
    }

    public PlaybackState getPlaybackState() {
        MediaController controller = activeController;
        if (controller == null) {
            return null;
        }
        return controller.getPlaybackState();
    }

    public Metadata getMetadata() {
        MediaController controller = activeController;
        if (controller == null) {
            return null;
        }

        MediaMetadata mediaMetadata = controller.getMetadata();
        if (mediaMetadata == null) {
            return null;
        }

        String title = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(artist)) {
            return null;
        }

        return new Metadata(title, artist);
    }

    public long getDuration() {
        MediaController controller = activeController;
        if (controller == null) {
            return 0;
        }

        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) {
            return 0;
        }

        return metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    public static class Metadata {
        private final String title;
        private final String artist;

        public Metadata(String title, String artist) {
            this.title = title;
            this.artist = artist;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
        }
    }
}
