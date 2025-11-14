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
    private val lastKnownPackageByWindowId = mutableMapOf<Int, String>()
    private var lastKnownFocusedPackage: String? = null

    fun evaluate(windows: List<AccessibilityWindowInfo>?, metrics: DisplayMetrics): WindowInfo {
        if (windows.isNullOrEmpty()) return WindowInfo.Empty

        val bestByPackage = mutableMapOf<String, WindowCandidate>()
        var focusedPackage: String? = null
        val observedWindowIds = mutableSetOf<Int>()

        windows.forEach { window ->
            val windowId = window.id
            observedWindowIds += windowId

            val bounds = Rect().also(window::getBoundsInScreen)
            val coverage = coverage(bounds, metrics)
            val state = determineWindowState(window, coverage)

            val root = window.root
            if (root == null) {
                if (state == WindowState.PICTURE_IN_PICTURE) {
                    val fallbackPackage = lastKnownPackageByWindowId[windowId]
                        ?: focusedPackage
                        ?: lastKnownFocusedPackage
                    if (!fallbackPackage.isNullOrEmpty() && fallbackPackage in SUPPORTED_PACKAGES) {
                        if (window.isActive && focusedPackage == null) {
                            focusedPackage = fallbackPackage
                        }
                        val candidate = WindowCandidate(
                            info = AppWindowInfo(
                                packageName = fallbackPackage,
                                state = WindowState.PICTURE_IN_PICTURE,
                                hasVisibleVideoSurface = true,
                            ),
                            coverage = coverage,
                        )
                        val existing = bestByPackage[fallbackPackage]
                        if (existing == null || candidate.isBetterThan(existing)) {
                            bestByPackage[fallbackPackage] = candidate
                        }
                        lastKnownPackageByWindowId[windowId] = fallbackPackage
                    }
                }
                return@forEach
            }
            try {
                val packageName = root.packageName?.toString()
                if (packageName.isNullOrEmpty()) {
                    return@forEach
                }

                if (window.isActive && focusedPackage == null) {
                    focusedPackage = packageName
                }

                if (packageName !in SUPPORTED_PACKAGES) {
                    return@forEach
                }

                val likelyVideoSurface by lazy { hasLikelyVideoSurface(root) }
                val assumeVideoSurface = when (state) {
                    WindowState.MINIMIZED_IN_APP,
                    WindowState.PICTURE_IN_PICTURE -> true
                    else -> false
                }

                val hasVideoSurface = when (packageName) {
                    YOUTUBE -> when (state) {
                        WindowState.BACKGROUND -> false
                        WindowState.FULLSCREEN -> likelyVideoSurface
                        WindowState.MINIMIZED_IN_APP,
                        WindowState.PICTURE_IN_PICTURE -> true
                    }
                    YOUTUBE_MUSIC -> when (state) {
                        WindowState.BACKGROUND -> false
                        WindowState.FULLSCREEN -> true
                        WindowState.MINIMIZED_IN_APP,
                        WindowState.PICTURE_IN_PICTURE -> assumeVideoSurface || likelyVideoSurface
                    }
                    else -> false
                }

                val candidate = WindowCandidate(
                    info = AppWindowInfo(
                        packageName = packageName,
                        state = state,
                        hasVisibleVideoSurface = hasVideoSurface,
                    ),
                    coverage = coverage,
                )

                val existing = bestByPackage[packageName]
                if (existing == null || candidate.isBetterThan(existing)) {
                    bestByPackage[packageName] = candidate
                }
                lastKnownPackageByWindowId[windowId] = packageName
            } finally {
                root.recycle()
            }
        }

        if (bestByPackage.isEmpty()) {
            lastKnownFocusedPackage = focusedPackage ?: lastKnownFocusedPackage
            lastKnownPackageByWindowId.keys.retainAll(observedWindowIds)
            return WindowInfo.Empty
        }

        lastKnownFocusedPackage = focusedPackage ?: lastKnownFocusedPackage
        lastKnownPackageByWindowId.keys.retainAll(observedWindowIds)

        return WindowInfo(
            focusedPackage = focusedPackage,
            appWindows = bestByPackage.mapValues { it.value.info },
        )
    }

    private fun determineWindowState(
        window: AccessibilityWindowInfo,
        coverage: Float,
    ): WindowState {
        // AccessibilityWindowInfo does not expose a TYPE_PINNED constant on this SDK level.
        // Fall back to heuristic coverage thresholds for identifying Picture-in-Picture windows.
        if (coverage <= BACKGROUND_COVERAGE_THRESHOLD) {
            return WindowState.BACKGROUND
        }
        if (coverage <= PIP_COVERAGE_THRESHOLD) {
            return WindowState.PICTURE_IN_PICTURE
        }
        return if (coverage >= FULLSCREEN_COVERAGE_THRESHOLD) {
            WindowState.FULLSCREEN
        } else {
            WindowState.MINIMIZED_IN_APP
        }
    }

    private fun coverage(bounds: Rect, metrics: DisplayMetrics): Float {
        val screenArea = (metrics.widthPixels * metrics.heightPixels).coerceAtLeast(1)
        val width = bounds.width().coerceAtLeast(0)
        val height = bounds.height().coerceAtLeast(0)
        val windowArea = width * height
        return if (windowArea == 0) 0f else windowArea.toFloat() / screenArea.toFloat()
    }

    private fun hasLikelyVideoSurface(root: AccessibilityNodeInfo): Boolean {
        if (findVideoSurface(root)) return true
        // Fullscreen windows occasionally fail the heuristics due to obfuscated class names.
        return false
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

    private data class WindowCandidate(
        val info: AppWindowInfo,
        val coverage: Float,
    ) {
        fun isBetterThan(other: WindowCandidate): Boolean {
            val thisPriority = STATE_PRIORITY[info.state] ?: 0
            val otherPriority = STATE_PRIORITY[other.info.state] ?: 0
            return when {
                thisPriority > otherPriority -> true
                thisPriority < otherPriority -> false
                else -> coverage > other.coverage
            }
        }
    }

    data class VideoHeuristics(
        val surfaceClasses: List<String>,
        val keywords: List<String>
    )

    companion object {
        private const val YOUTUBE = "com.google.android.youtube"
        private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        private val SUPPORTED_PACKAGES = setOf(YOUTUBE, YOUTUBE_MUSIC)

        private const val FULLSCREEN_COVERAGE_THRESHOLD = 0.75f
        private const val PIP_COVERAGE_THRESHOLD = 0.12f
        private const val BACKGROUND_COVERAGE_THRESHOLD = 0.01f

        private val STATE_PRIORITY = mapOf(
            WindowState.FULLSCREEN to 3,
            WindowState.MINIMIZED_IN_APP to 2,
            WindowState.PICTURE_IN_PICTURE to 2,
            WindowState.BACKGROUND to 0,
        )
    }
}
