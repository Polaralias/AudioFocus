package com.polaralias.audiofocus.window

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WindowHeuristics(
    context: Context,
    private val nowProvider: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val heuristics: VideoHeuristics = loadHeuristics(context)
    private val surfaceHints: List<SurfaceHint> =
        (heuristics.surfaceHints + EXTRA_SURFACE_HINTS).ifEmpty { FALLBACK_HINTS + EXTRA_SURFACE_HINTS }
    private val inferenceStore = WindowInferenceStore
    private var lastKnownFocusedPackage: String? = null
    private var lastVisibleSnapshot: WindowInfo = WindowInfo.Empty
    private var lastVisibleSnapshotTimestamp: Long = 0L

    private val _cacheTelemetry = MutableStateFlow(WindowCacheTelemetry())
    val cacheTelemetry: StateFlow<WindowCacheTelemetry> = _cacheTelemetry

    fun evaluate(windows: List<AccessibilityWindowInfo>?, metrics: DisplayMetrics): WindowInfo {
        val windowList = windows.orEmpty()
        val observedWindowIds = mutableSetOf<Int>()
        if (windowList.isEmpty()) {
            inferenceStore.retainWindowIds(observedWindowIds)
            return resolveEmptyResult(CacheReason.NO_WINDOWS)
        }

        val bestByPackage = mutableMapOf<String, WindowCandidate>()
        var focusedPackage: String? = null
        var sawTransientSystemWindow = false
        var sawNonTransientWindow = false

        val packagesByBounds = mutableMapOf<BoundsSignature, String>()
        val pendingPipWindows = mutableListOf<PendingPipWindow>()

        windowList.forEach { window ->
            val windowId = window.id
            observedWindowIds += windowId

            val bounds = Rect().also(window::getBoundsInScreen)
            val coverage = coverage(bounds, metrics)
            val boundsSignature = BoundsSignature.from(bounds)
            val state = determineWindowState(window, coverage)

            val root = window.root
            val packageName = root?.packageName?.toString()
            val isTransientSystemWindow = isTransientSystemWindow(window, packageName)
            if (isTransientSystemWindow) {
                sawTransientSystemWindow = true
            } else {
                sawNonTransientWindow = true
            }
            if (root == null) {
                if (state == WindowState.PICTURE_IN_PICTURE) {
                    pendingPipWindows += PendingPipWindow(
                        id = windowId,
                        coverage = coverage,
                        boundsSignature = boundsSignature,
                        isActive = window.isActive,
                        title = window.title?.toString(),
                    )
                }
                return@forEach
            }
            try {
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
                inferenceStore.rememberWindowPackage(windowId, packageName)
                packagesByBounds[boundsSignature] = packageName
            } finally {
                root.recycle()
            }
        }

        if (pendingPipWindows.isNotEmpty()) {
            val activeMediaPackage = inferenceStore.activeMediaPackage()
            pendingPipWindows.forEach { pending ->
                val inference = inferPackageForPiP(
                    pending = pending,
                    packagesByBounds = packagesByBounds,
                    focusedPackage = focusedPackage,
                    activeMediaPackage = activeMediaPackage,
                )
                val inferredPackage = inference?.packageName
                if (inferredPackage == null) {
                    Log.w(
                        TAG,
                        "Unable to infer package for PiP window ${pending.id} title='${pending.title}'"
                    )
                    return@forEach
                }
                if (pending.isActive && focusedPackage == null) {
                    focusedPackage = inferredPackage
                }
                val candidate = WindowCandidate(
                    info = AppWindowInfo(
                        packageName = inferredPackage,
                        state = WindowState.PICTURE_IN_PICTURE,
                        videoSurfaceFraction = pending.coverage.coerceIn(0f, 1f),
                        playMode = PlayMode.VIDEO,
                    ),
                    coverage = pending.coverage,
                )
                val existing = bestByPackage[inferredPackage]
                if (existing == null || candidate.isBetterThan(existing)) {
                    bestByPackage[inferredPackage] = candidate
                }
                inferenceStore.rememberWindowPackage(pending.id, inferredPackage)
                Log.i(
                    TAG,
                    "Inferred PiP window ${pending.id} -> $inferredPackage via ${inference.reason}"
                )
            }
        }

        val supportedFocusedPackage = focusedPackage?.takeIf { it in SUPPORTED_PACKAGES }

        lastKnownFocusedPackage = supportedFocusedPackage ?: lastKnownFocusedPackage
        inferenceStore.retainWindowIds(observedWindowIds)

        if (bestByPackage.isEmpty()) {
            val reason = if (sawTransientSystemWindow && !sawNonTransientWindow) {
                CacheReason.TRANSIENT_SYSTEM
            } else {
                CacheReason.NO_SUPPORTED_WINDOWS
            }
            return resolveEmptyResult(reason)
        }

        val info = WindowInfo(
            focusedPackage = supportedFocusedPackage,
            appWindows = bestByPackage.mapValues { it.value.info },
        )

        val hasVisibleEntry = info.appWindows.values.any { it.state != WindowState.BACKGROUND }
        if (hasVisibleEntry) {
            storeSnapshot(info)
            return info
        }

        clearSnapshot(CacheReason.BACKGROUND)
        return WindowInfo.Empty
    }

    private fun inferPackageForPiP(
        pending: PendingPipWindow,
        packagesByBounds: Map<BoundsSignature, String>,
        focusedPackage: String?,
        activeMediaPackage: String?,
    ): PipInferenceResult? {
        val orderedCandidates = listOfNotNull(
            inferenceStore.lastKnownPackage(pending.id)?.let {
                PipInferenceResult(it, PipInferenceReason.LAST_KNOWN_WINDOW)
            },
            inferPackageFromTitle(pending.title)?.let {
                PipInferenceResult(it, PipInferenceReason.TITLE_HINT)
            },
            packagesByBounds[pending.boundsSignature]?.let {
                PipInferenceResult(it, PipInferenceReason.BOUNDS_MATCH)
            },
            activeMediaPackage?.let {
                PipInferenceResult(it, PipInferenceReason.ACTIVE_MEDIA_SESSION)
            },
            focusedPackage?.let { PipInferenceResult(it, PipInferenceReason.FOCUSED_PACKAGE) },
            lastKnownFocusedPackage?.let {
                PipInferenceResult(it, PipInferenceReason.LAST_FOCUSED_PACKAGE)
            },
        )
        return orderedCandidates.firstOrNull { it.packageName in SUPPORTED_PACKAGES }
    }

    private fun inferPackageFromTitle(title: String?): String? {
        if (title.isNullOrEmpty()) return null
        val normalized = title.lowercase(Locale.US)
        return when {
            normalized.contains("youtube music") -> YOUTUBE_MUSIC
            normalized.contains("youtube") -> YOUTUBE
            else -> null
        }
    }

    private fun resolveEmptyResult(reason: CacheReason): WindowInfo {
        val lastState = lastVisibleSnapshot.appWindows.values.firstOrNull()?.state
        val wasPip = lastState == WindowState.PICTURE_IN_PICTURE
        val shouldUseCache = reason == CacheReason.NO_WINDOWS ||
            reason == CacheReason.TRANSIENT_SYSTEM ||
            (wasPip && reason == CacheReason.NO_SUPPORTED_WINDOWS)

        if (shouldUseCache) {
            val cached = cachedSnapshotOrNull()
            if (cached != null) {
                Log.d(TAG, "Returning cached window snapshot due to $reason")
                recordTelemetry(CacheAction.RETURNED, reason)
                return cached
            }
            recordTelemetry(CacheAction.MISS, reason)
        } else if (reason == CacheReason.NO_SUPPORTED_WINDOWS) {
            clearSnapshot(reason)
        }
        return WindowInfo.Empty
    }

    private fun determineWindowState(
        window: AccessibilityWindowInfo,
        coverage: Float,
    ): WindowState {
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

            val structuralSurface = isStructuralVideoNode(node, className, label)
            val matchedHint = matchSurfaceHint(className, viewId, label)
            if ((structuralSurface || matchedHint != null) &&
                (node.isVisibleToUser || matchedHint?.allowHidden == true)
            ) {
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

    private fun isStructuralVideoNode(
        node: AccessibilityNodeInfo,
        className: String,
        label: String,
    ): Boolean {
        if (className.isEmpty()) return false

        if (className in VIDEO_SURFACE_CLASSES || VIDEO_SURFACE_CLASS_HINTS.any { className.contains(it) }) {
            return node.isVisibleToUser
        }

        if (VIDEO_CONTAINER_CLASSES.any { className.contains(it) }) {
            val description = node.contentDescription?.toString()?.lowercase(Locale.US).orEmpty()
            if (VIDEO_LABEL_KEYWORDS.any { label.contains(it) || description.contains(it) }) {
                return node.isVisibleToUser
            }
        }

        return false
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

    private fun cachedSnapshotOrNull(): WindowInfo? {
        if (lastVisibleSnapshot == WindowInfo.Empty) return null
        val age = nowProvider() - lastVisibleSnapshotTimestamp
        if (age > CACHE_TIMEOUT_MS) {
            Log.v(TAG, "Cached window snapshot expired after ${age}ms")
            clearSnapshot(CacheReason.EXPIRED)
            return null
        }
        return lastVisibleSnapshot
    }

    private fun storeSnapshot(info: WindowInfo) {
        lastVisibleSnapshot = info
        lastVisibleSnapshotTimestamp = nowProvider()
        Log.v(
            TAG,
            "Cached window snapshot for packages=${info.appWindows.keys} focus=${info.focusedPackage}"
        )
        recordTelemetry(CacheAction.STORED, CacheReason.VISIBLE_SNAPSHOT)
    }

    private fun clearSnapshot(reason: CacheReason) {
        if (lastVisibleSnapshot == WindowInfo.Empty) return
        lastVisibleSnapshot = WindowInfo.Empty
        lastVisibleSnapshotTimestamp = 0L
        Log.v(TAG, "Cleared cached window snapshot due to $reason")
        recordTelemetry(CacheAction.CLEARED, reason)
    }

    private fun recordTelemetry(action: CacheAction, reason: CacheReason) {
        val previous = _cacheTelemetry.value
        val updated = when (action) {
            CacheAction.STORED -> previous.copy(
                totalStores = previous.totalStores + 1,
                lastAction = action,
                lastReason = reason,
                timestamp = nowProvider(),
            )
            CacheAction.RETURNED -> previous.copy(
                totalHits = previous.totalHits + 1,
                lastAction = action,
                lastReason = reason,
                timestamp = nowProvider(),
            )
            CacheAction.CLEARED -> previous.copy(
                totalClears = previous.totalClears + 1,
                lastAction = action,
                lastReason = reason,
                timestamp = nowProvider(),
            )
            CacheAction.MISS -> previous.copy(
                totalMisses = previous.totalMisses + 1,
                lastAction = action,
                lastReason = reason,
                timestamp = nowProvider(),
            )
            CacheAction.NONE -> previous
        }
        _cacheTelemetry.value = updated
    }

    private fun isTransientSystemWindow(
        window: AccessibilityWindowInfo,
        packageName: String?,
    ): Boolean {
        if (packageName in TRANSIENT_SYSTEM_PACKAGES) return true
        if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
            window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
        ) {
            return true
        }
        val title = window.title?.toString()?.lowercase(Locale.US).orEmpty()
        if (title.isNotEmpty() && TRANSIENT_TITLE_KEYWORDS.any { title.contains(it) }) {
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "WindowHeuristics"
        private const val YOUTUBE = "com.google.android.youtube"
        private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        private val SUPPORTED_PACKAGES = setOf(YOUTUBE, YOUTUBE_MUSIC)

        private const val FULLSCREEN_COVERAGE_THRESHOLD = 0.75f
        private const val PIP_COVERAGE_THRESHOLD = 0.12f
        private const val BACKGROUND_COVERAGE_THRESHOLD = 0.01f
        private const val CACHE_TIMEOUT_MS = 1000L

        private const val MAX_SELECTION_PARENT_DEPTH = 5

        private val VIDEO_SURFACE_CLASSES = setOf(
            "android.view.surfaceview",
            "android.view.textureview",
        )
        private val VIDEO_SURFACE_CLASS_HINTS = listOf(
            "surfaceview",
            "textureview",
            "videoview",
            "playerview",
        )
        private val VIDEO_CONTAINER_CLASSES = listOf(
            "android.view.viewgroup",
            "android.widget.framelayout",
        )
        private val VIDEO_LABEL_KEYWORDS = listOf("video", "player")

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

        private val SHORTS_KEYWORDS = listOf("shorts", "reel", "reels", "shortform", "clip", "shorts_player", "reel_player")
        private val SHORTS_CLASS_HINTS = listOf("shorts", "shortvideo", "reel")
        private val SHORTS_CONTAINER_CLASSES = listOf("viewpager", "recyclerview", "framelayout")

        private val YTM_VIDEO_KEYWORDS = listOf("video", "videos")
        private val YTM_AUDIO_KEYWORDS = listOf("song", "songs", "audio")

        private val TRANSIENT_SYSTEM_PACKAGES = setOf("com.android.systemui")
        private val TRANSIENT_TITLE_KEYWORDS = listOf("notification", "volume", "controls", "screenshot")
    }
}

private data class PendingPipWindow(
    val id: Int,
    val coverage: Float,
    val boundsSignature: BoundsSignature,
    val isActive: Boolean,
    val title: String?,
)

private data class BoundsSignature(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        fun from(rect: Rect): BoundsSignature {
            return BoundsSignature(rect.left, rect.top, rect.right, rect.bottom)
        }
    }
}

private data class PipInferenceResult(
    val packageName: String,
    val reason: PipInferenceReason,
)

private enum class PipInferenceReason {
    LAST_KNOWN_WINDOW,
    TITLE_HINT,
    BOUNDS_MATCH,
    ACTIVE_MEDIA_SESSION,
    FOCUSED_PACKAGE,
    LAST_FOCUSED_PACKAGE,
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
