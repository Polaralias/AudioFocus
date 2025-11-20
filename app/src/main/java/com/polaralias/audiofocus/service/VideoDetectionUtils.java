package com.polaralias.audiofocus.service;

import android.view.accessibility.AccessibilityNodeInfo;

public final class VideoDetectionUtils {
    private VideoDetectionUtils() {
        // Utility class
    }

    public static boolean hasVisibleVideoSurface(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        if (node.isVisibleToUser()) {
            CharSequence className = node.getClassName();
            if (className != null) {
                String name = className.toString();
                if ("android.view.SurfaceView".equals(name) || "android.view.TextureView".equals(name)) {
                    return true;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    if (hasVisibleVideoSurface(child)) {
                        return true;
                    }
                } finally {
                    child.recycle();
                }
            }
        }

        return false;
    }
}
