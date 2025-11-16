package com.polaralias.audiofocus.window

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.max

class WindowHeuristics(context: Context) {
    private val heuristics: VideoHeuristics = loadHeuristics(context)
    private val surfaceHints: List<SurfaceHint> =
        (heuristics.surfaceHints + EXTRA_SURFACE_HINTS).ifEmpty { FALLBACK_HINTS + EXTRA_SURFACE_HINTS }
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
                                videoSurfaceFraction = coverage.coerceIn(0f, 1f),
                                playMode = PlayMode.VIDEO,
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

                val isSupportedPackage = packageName in SUPPORTED_PACKAGES
                if (window.isActive && focusedPackage == null && isSupportedPackage) {
                    focusedPackage = packageName
                }

                if (!isSupportedPackage) {
                    return@forEach
                }

                val analysis = analyzeWindow(packageName, root, metrics)
                val videoSurfaceFraction = if (state == WindowState.BACKGROUND) 0f else analysis.videoSurfaceFraction
                val playMode = if (state == WindowState.BACKGROUND) PlayMode.AUDIO else analysis.playMode

                val candidate = WindowCandidate(
                    info = AppWindowInfo(
                        packageName = packageName,
                        state = state,
                        videoSurfaceFraction = videoSurfaceFraction,
                        playMode = playMode,
                        selectedMode = analysis.selectedMode,
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

        val supportedFocusedPackage = focusedPackage?.takeIf { it in SUPPORTED_PACKAGES }

        if (bestByPackage.isEmpty()) {
            lastKnownFocusedPackage = supportedFocusedPackage ?: lastKnownFocusedPackage
            lastKnownPackageByWindowId.keys.retainAll(observedWindowIds)
            return WindowInfo.Empty
        }

        lastKnownFocusedPackage = supportedFocusedPackage ?: lastKnownFocusedPackage
        lastKnownPackageByWindowId.keys.retainAll(observedWindowIds)

        return WindowInfo(
            focusedPackage = supportedFocusedPackage,
            appWindows = bestByPackage.mapValues { it.value.info },
        )
    }

    private fun determineWindowState(
        window: AccessibilityWindowInfo,
        coverage: Float,
    ): WindowState {
        // TYPE_PINNED (value 4) was added in API 26 for Picture-in-Picture windows
        if (window.type == 4) {
            return WindowState.PICTURE_IN_PICTURE
        }
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

    private fun analyzeWindow(
        packageName: String,
        root: AccessibilityNodeInfo,
        metrics: DisplayMetrics,
    ): WindowAnalysis {
        val screenArea = (metrics.widthPixels * metrics.heightPixels).coerceAtLeast(1).toFloat()
        var maxSurfaceFraction = 0f
        var shortsDetected = false
        var youtubeMusicSelection: PlayMode? = null

        traverseNodes(root) { node ->
            val label = buildLabel(node)
            val viewId = node.viewIdResourceName?.lowercase(Locale.US).orEmpty()
            val className = node.className?.toString()?.lowercase(Locale.US).orEmpty()

            val matchedHint = matchSurfaceHint(className, viewId, label)
            if (matchedHint != null && (node.isVisibleToUser || matchedHint.allowHidden)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val width = bounds.width().coerceAtLeast(0)
                val height = bounds.height().coerceAtLeast(0)
                val area = width * height
                if (area > 0) {
                    val fraction = (area.toFloat() / screenArea).coerceIn(0f, 1f)
                    maxSurfaceFraction = max(maxSurfaceFraction, fraction)
                }
            }

            if (packageName == YOUTUBE && !shortsDetected) {
                shortsDetected = indicatesShorts(label, viewId, className)
            }

            if (packageName == YOUTUBE_MUSIC && youtubeMusicSelection == null) {
                youtubeMusicSelection = detectYouTubeMusicSelection(node, label)
            }
        }

        val playMode = when (packageName) {
            YOUTUBE -> when {
                maxSurfaceFraction > 0f && shortsDetected -> PlayMode.SHORTS
                maxSurfaceFraction > 0f -> PlayMode.VIDEO
                else -> PlayMode.AUDIO
            }
            YOUTUBE_MUSIC -> when {
                maxSurfaceFraction > 0f -> PlayMode.VIDEO
                youtubeMusicSelection != null -> youtubeMusicSelection!!
                else -> PlayMode.AUDIO
            }
            else -> if (maxSurfaceFraction > 0f) PlayMode.VIDEO else PlayMode.AUDIO
        }

        return WindowAnalysis(
            videoSurfaceFraction = maxSurfaceFraction,
            playMode = playMode,
            selectedMode = youtubeMusicSelection,
        )
    }

    private fun matchSurfaceHint(
        className: String,
        viewId: String,
        label: String,
    ): SurfaceHint? {
        if (surfaceHints.isEmpty()) return null
        return surfaceHints.firstOrNull { it.matches(className, viewId, label) }
    }

    private fun traverseNodes(root: AccessibilityNodeInfo, visitor: (AccessibilityNodeInfo) -> Unit) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(AccessibilityNodeInfo.obtain(root))
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            try {
                visitor(node)
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { stack.addLast(it) }
                }
            } finally {
                node.recycle()
            }
        }
    }

    private fun indicatesShorts(
        label: String,
        viewId: String,
        className: String,
    ): Boolean {
        if (SHORTS_KEYWORDS.any { label.contains(it) || viewId.contains(it) }) {
            return true
        }
        if (SHORTS_CLASS_HINTS.any { className.contains(it) }) {
            return true
        }
        if (SHORTS_CONTAINER_CLASSES.any { className.contains(it) } &&
            SHORTS_KEYWORDS.any { viewId.contains(it) || label.contains(it) }
        ) {
            return true
        }
        return false
    }

    private fun detectYouTubeMusicSelection(
        node: AccessibilityNodeInfo,
        label: String,
    ): PlayMode? {
        if (label.isEmpty()) return null
        val inferred = when {
            YTM_VIDEO_KEYWORDS.any { label.contains(it) } -> PlayMode.VIDEO
            YTM_AUDIO_KEYWORDS.any { label.contains(it) } -> PlayMode.AUDIO
            else -> null
        } ?: return null
        return if (isNodeOrAncestorSelected(node)) inferred else null
    }

    private fun isNodeOrAncestorSelected(node: AccessibilityNodeInfo): Boolean {
        var depth = 0
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        while (current != null && depth < MAX_SELECTION_PARENT_DEPTH) {
            val selected = current.isSelected ||
                current.isChecked ||
                current.isAccessibilityFocused ||
                current.stateDescription?.contains("selected", ignoreCase = true) == true ||
                current.contentDescription?.toString()?.contains("selected", ignoreCase = true) == true
            if (selected) {
                current.recycle()
                return true
            }
            val parent = current.parent
            current.recycle()
            current = parent
            depth++
        }
        current?.recycle()
        return false
    }

    private fun buildLabel(node: AccessibilityNodeInfo): String {
        val pieces = mutableListOf<String>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { pieces += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { pieces += it }
        if (pieces.isEmpty()) return ""
        return pieces.joinToString(separator = " ").lowercase(Locale.US)
    }

    private fun loadHeuristics(context: Context): VideoHeuristics {
        return runCatching {
            context.assets.open("video_heuristics.json").bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val classHints = json.getJSONArray("surface_classes")
                    .toStringList()
                    .mapNotNull { it.takeIf(String::isNotEmpty)?.lowercase(Locale.US) }
                    .map { SurfaceHint(classSubstrings = listOf(it)) }
                val keywordHints = json.getJSONArray("keywords")
                    .toStringList()
                    .mapNotNull { it.takeIf(String::isNotEmpty)?.lowercase(Locale.US) }
                    .map { SurfaceHint(labelSubstrings = listOf(it)) }
                VideoHeuristics(
                    surfaceHints = classHints + keywordHints,
                )
            }
        }.getOrElse {
            VideoHeuristics(surfaceHints = FALLBACK_HINTS)
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

    companion object {
        private const val YOUTUBE = "com.google.android.youtube"
        private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        private val SUPPORTED_PACKAGES = setOf(YOUTUBE, YOUTUBE_MUSIC)

        private const val FULLSCREEN_COVERAGE_THRESHOLD = 0.75f
        private const val PIP_COVERAGE_THRESHOLD = 0.12f
        private const val BACKGROUND_COVERAGE_THRESHOLD = 0.01f

        private const val MAX_SELECTION_PARENT_DEPTH = 5

        private val STATE_PRIORITY = mapOf(
            WindowState.FULLSCREEN to 3,
            WindowState.MINIMIZED_IN_APP to 2,
            WindowState.PICTURE_IN_PICTURE to 2,
            WindowState.BACKGROUND to 0,
        )

        private val FALLBACK_HINTS = listOf(
            SurfaceHint(classSubstrings = listOf("surfaceview")),
            SurfaceHint(classSubstrings = listOf("textureview")),
            SurfaceHint(classSubstrings = listOf("playerview")),
            SurfaceHint(labelSubstrings = listOf("video")),
            SurfaceHint(labelSubstrings = listOf("player")),
        )

        private val EXTRA_SURFACE_HINTS = listOf(
            SurfaceHint(viewIdSubstrings = listOf("player_view"), allowHidden = true),
            SurfaceHint(viewIdSubstrings = listOf("video_surface")),
            SurfaceHint(viewIdSubstrings = listOf("watch_player"), allowHidden = true),
            SurfaceHint(viewIdSubstrings = listOf("shorts_player"), allowHidden = true),
            SurfaceHint(viewIdSubstrings = listOf("miniplayer"), allowHidden = true),
            SurfaceHint(viewIdSubstrings = listOf("pip"), allowHidden = true),
        )

        private val SHORTS_KEYWORDS = listOf("shorts", "reel", "reels", "shortform", "clip")
        private val SHORTS_CLASS_HINTS = listOf("shorts", "shortvideo")
        private val SHORTS_CONTAINER_CLASSES = listOf("viewpager", "recyclerview")

        private val YTM_VIDEO_KEYWORDS = listOf("video", "videos")
        private val YTM_AUDIO_KEYWORDS = listOf("song", "songs", "audio")
    }
}

private data class WindowAnalysis(
    val videoSurfaceFraction: Float,
    val playMode: PlayMode,
    val selectedMode: PlayMode?,
)

data class VideoHeuristics(
    val surfaceHints: List<SurfaceHint>,
)

data class SurfaceHint(
    val classSubstrings: List<String> = emptyList(),
    val viewIdSubstrings: List<String> = emptyList(),
    val labelSubstrings: List<String> = emptyList(),
    val allowHidden: Boolean = false,
) {
    fun matches(
        className: String,
        viewId: String,
        label: String,
    ): Boolean {
        if (classSubstrings.any { className.contains(it) }) return true
        if (viewIdSubstrings.any { viewId.contains(it) }) return true
        if (labelSubstrings.any { label.contains(it) }) return true
        return false
    }
}
