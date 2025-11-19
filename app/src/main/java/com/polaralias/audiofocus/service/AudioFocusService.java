package com.polaralias.audiofocus.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import java.util.List;

public class AudioFocusService extends Service implements MediaSessionManager.OnActiveSessionsChangedListener {
    private static final String PACKAGE_YOUTUBE = "com.google.android.youtube";
    private static final String PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music";

    private MediaSessionManager mediaSessionManager;
    private MediaControllerManager controllerManager;
    private Handler mainHandler;
    private OverlayView overlayView;
    private WindowManager windowManager;

    @Override
    public void onCreate() {
        super.onCreate();
        controllerManager = new MediaControllerManager();
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new OverlayView(this, controllerManager);

        if (mediaSessionManager != null) {
            mediaSessionManager.addOnActiveSessionsChangedListener(this, null, mainHandler);
            onActiveSessionsChanged(mediaSessionManager.getActiveSessions(null));
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(this);
        }
        if (controllerManager != null) {
            controllerManager.setActiveController(null);
        }
        hideOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        MediaController target = null;
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (isSupported(controller.getPackageName())) {
                    target = controller;
                    break;
                }
            }
        }
        controllerManager.setActiveController(target);
    }

    private boolean isSupported(String packageName) {
        return PACKAGE_YOUTUBE.equals(packageName) || PACKAGE_YOUTUBE_MUSIC.equals(packageName);
    }

    public void showOverlay() {
        if (overlayView == null || windowManager == null) {
            return;
        }

        View view = overlayView.getView();
        if (view.getParent() == null) {
            windowManager.addView(view, overlayView.getLayoutParams());
            overlayView.updateFromController();
        }
    }

    public void hideOverlay() {
        if (overlayView == null || windowManager == null) {
            return;
        }

        View view = overlayView.getView();
        if (view.getParent() != null) {
            windowManager.removeView(view);
            overlayView.stopHeartbeat();
        }
    }

    public MediaControllerManager getControllerManager() {
        return controllerManager;
    }
}
