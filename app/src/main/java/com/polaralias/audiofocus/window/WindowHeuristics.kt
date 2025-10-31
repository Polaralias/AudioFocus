package com.polaralias.audiofocus.window

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject

class WindowHeuristics(context: Context) {
    private val heuristics: VideoHeuristics = loadHeuristics(context)

    fun evaluate(windows: List<AccessibilityWindowInfo>?, metrics: DisplayMetrics): WindowInfo {
        if (windows.isNullOrEmpty()) return WindowInfo.Empty
        val ytWindow = windows.firstOrNull {
            it.root?.packageName == YOUTUBE_MUSIC
        } ?: return WindowInfo.Empty
        val bounds = Rect()
        ytWindow.getBoundsInScreen(bounds)
        val isFullscreen = bounds.height() >= metrics.heightPixels * FULLSCREEN_THRESHOLD &&
            bounds.width() >= metrics.widthPixels * FULLSCREEN_THRESHOLD
        val root = ytWindow.root ?: return WindowInfo(isFullscreen, isFullscreen)
        val hasSurface = try {
            findVideoSurface(root)
        } finally {
            root.recycle()
        }
        return WindowInfo(isFullscreen = isFullscreen, hasLikelyVideoSurface = hasSurface || isFullscreen)
    }

    private fun findVideoSurface(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        if (heuristics.surfaceClasses.any { className.contains(it, ignoreCase = false) }) {
            return true
        }
        val text = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .joinToString(separator = " ").lowercase()
        if (heuristics.keywords.any { text.contains(it) }) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findVideoSurface(child)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun loadHeuristics(context: Context): VideoHeuristics {
        return runCatching {
            context.assets.open("video_heuristics.json").bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                VideoHeuristics(
                    surfaceClasses = json.getJSONArray("surface_classes").toStringList(),
                    keywords = json.getJSONArray("keywords").toStringList()
                )
            }
        }.getOrElse {
            VideoHeuristics(
                surfaceClasses = listOf("SurfaceView", "TextureView", "PlayerView"),
                keywords = listOf("video", "player")
            )
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until length()) {
            result.add(optString(i))
        }
        return result
    }

    data class VideoHeuristics(
        val surfaceClasses: List<String>,
        val keywords: List<String>
    )

    companion object {
        private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        private const val FULLSCREEN_THRESHOLD = 0.96f
    }
}
