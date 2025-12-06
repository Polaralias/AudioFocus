# Phase 3: Monitors

**Role:** Android Engineer
**Skill:** `android-engineering-core`

## Objective
Implement the monitoring components that detect app state, media playback, and window visibility.

## References
*   `../technical_specification.md` (Section 2.2 Playback detection requirements, Section 4.1 Component overview)

## Instructions

1.  **Architecture**
    *   Design a reactive architecture (using Kotlin Flows) where monitors emit state updates to a central collector.
    *   Define a data structure for each monitor's output (e.g., `AccessibilityState`, `MediaSessionState`).

2.  **Accessibility Monitor**
    *   In `AppAccessibilityService`:
        *   Filter events for `com.google.android.youtube` and `com.google.android.apps.youtube.music`.
        *   On `TYPE_WINDOW_CONTENT_CHANGED` or `TYPE_WINDOW_STATE_CHANGED`:
            *   Traverse the node tree.
            *   Look for video surfaces (`android.view.SurfaceView`, `android.view.TextureView` or specific app view IDs if known/generic).
    *   Determine `WindowState`:
        *   Check node bounds vs screen size (Fullscreen detection).
        *   Check if nodes are visible.

3.  **Media Session Monitor**
    *   Create `MediaSessionMonitor` class.
    *   Inject `MediaSessionManager`.
    *   Observe `addOnActiveSessionsChangedListener` (requires Notification Access).
    *   Filter controllers for Target Apps.
    *   For each relevant controller, register `Callback`:
        *   Track `PlaybackState` (Playing, Paused).
        *   Track `Metadata` (Duration, ID - to detect ad vs content if possible, or just change).

4.  **Notification Monitor**
    *   In `AppNotificationListenerService`:
        *   Track notifications from Target Apps.
        *   Check for ongoing media style notifications.
        *   Use this as a fallback/confirmation for playback state.

5.  **Foreground App Detector**
    *   Implement `ForegroundAppDetector`.
    *   Primary: Use Accessibility Events (`TYPE_WINDOW_STATE_CHANGED`).
    *   Fallback: `UsageStatsManager.queryUsageStats` (if permission granted).
    *   Emit the current foreground package name.

6.  **Integration**
    *   Ensure all monitors can be injected into the `AudioFocusService`.
    *   Ensure they handle errors gracefully (e.g., permission revoked).
