package com.polaralias.audiofocus.service;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.polaralias.audiofocus.R;

/**
 * Renders and manages the full-screen overlay that mirrors media playback controls.
 */
public class OverlayView {
    private final Context context;
    private final MediaControllerManager controllerManager;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams layoutParams;
    private final View overlayRoot;
    private final TextView titleView;
    private final ImageButton playPauseButton;
    private final SeekBar seekBar;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            PlaybackState state = controllerManager.getPlaybackState();
            if (state != null) {
                playbackState = state.getState();
                lastKnownPosition = state.getPosition();
                long stateUpdateTime = state.getLastPositionUpdateTime();
                lastPositionUpdateTime = stateUpdateTime == 0 ? SystemClock.elapsedRealtime() : stateUpdateTime;
                long duration = controllerManager.getDuration();
                if (duration > 0 && seekBar.getMax() != duration) {
                    seekBar.setMax((int) duration);
                }
            }

            if (overlayRoot.getVisibility() == View.VISIBLE && playbackState == PlaybackState.STATE_PLAYING) {
                long elapsed = SystemClock.elapsedRealtime() - lastPositionUpdateTime;
                long currentPosition = lastKnownPosition + elapsed;
                seekBar.setProgress((int) currentPosition);
                handler.postDelayed(this, HEARTBEAT_DELAY_MS);
            }
        }
    };

    private static final int HEARTBEAT_DELAY_MS = 1000;

    private long lastKnownPosition = 0L;
    private long lastPositionUpdateTime = 0L;
    private int playbackState = PlaybackState.STATE_NONE;

    public OverlayView(Context context, MediaControllerManager controllerManager) {
        this.context = context.getApplicationContext();
        this.controllerManager = controllerManager;
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.layoutParams = buildLayoutParams();

        overlayRoot = LayoutInflater.from(this.context).inflate(R.layout.layout_video_overlay, null);
        titleView = overlayRoot.findViewById(R.id.overlayTitle);
        playPauseButton = overlayRoot.findViewById(R.id.overlayPlayPause);
        seekBar = overlayRoot.findViewById(R.id.overlaySeekBar);

        configureControls();
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private void configureControls() {
        playPauseButton.setOnClickListener(v -> {
            if (playbackState == PlaybackState.STATE_PLAYING) {
                controllerManager.pause();
            } else {
                controllerManager.play();
                startHeartbeat();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Intentionally empty; seeking occurs after the drag is finished.
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No-op
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                controllerManager.seekTo(seekBar.getProgress());
                lastKnownPosition = seekBar.getProgress();
                lastPositionUpdateTime = SystemClock.elapsedRealtime();
            }
        });
    }

    public void updateFromController() {
        MediaControllerManager.Metadata metadata = controllerManager.getMetadata();
        if (metadata != null) {
            if (metadata.getArtist() == null || metadata.getArtist().isEmpty()) {
                titleView.setText(metadata.getTitle());
            } else {
                titleView.setText(metadata.getTitle() + " - " + metadata.getArtist());
            }
        }

        PlaybackState state = controllerManager.getPlaybackState();
        if (state != null) {
            playbackState = state.getState();
            lastKnownPosition = state.getPosition();
            long stateUpdateTime = state.getLastPositionUpdateTime();
            lastPositionUpdateTime = stateUpdateTime == 0 ? SystemClock.elapsedRealtime() : stateUpdateTime;
            seekBar.setProgress((int) lastKnownPosition);

            long duration = controllerManager.getDuration();
            if (duration > 0) {
                seekBar.setMax((int) duration);
            }
        }
        startHeartbeat();
    }

    public void startHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_DELAY_MS);
    }

    public void stopHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
    }

    public View getView() {
        return overlayRoot;
    }

    public WindowManager.LayoutParams getLayoutParams() {
        return layoutParams;
    }

    public WindowManager getWindowManager() {
        return windowManager;
    }
}
