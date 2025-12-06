# Phase 5: Overlay UI

**Role:** Android Engineer
**Skills:** `android-ui-compose`, `android-engineering-core`

## Objective
Implement the system overlay window, its UI, and the media control interaction.

## References
*   `../technical_specification.md` (Section 2.3 Media controls, Section 2.4 Overlay customisation)

## Instructions

1.  **OverlayManager**
    *   Create `OverlayManager` class.
    *   Inject `WindowManager`.
    *   Implement `showOverlay()`, `hideOverlay()`, `updateOverlay()`.
    *   **Window Parameters:**
        *   Type: `TYPE_APPLICATION_OVERLAY`.
        *   Flags: `FLAG_NOT_TOUCH_MODAL` (allow outside touch), `FLAG_LAYOUT_IN_SCREEN`, `FLAG_LAYOUT_NO_LIMITS`.
        *   Format: `PIXEL_FORMAT_TRANSLUCENT`.
    *   Handle Layout Params updates (e.g., screen rotation).

2.  **Compose in Service**
    *   Create a `ComposeView` and attach it to the `WindowManager`.
    *   **Crucial:** You must manually manage the `LifecycleOwner` and `SavedStateRegistryOwner` for the ComposeView since it is not in an Activity. Ensure `setViewTreeLifecycleOwner` and `setViewTreeSavedStateRegistryOwner` are called.

3.  **Overlay UI (Compose)**
    *   Create `OverlayScreen` composable.
    *   **Visuals:**
        *   Background: Solid Color (Configurable) or Image.
        *   Opacity: 100% (as per spec "Opacity must always remain fixed at complete coverage").
    *   **Controls:**
        *   Play/Pause Button (Toggle).
        *   Skip Forward (10s).
        *   Skip Backward (10s).
        *   Progress Bar (Seekable).
    *   **State:**
        *   Observe `MediaSessionMonitor` for current progress and state.

4.  **Media Control Interaction**
    *   Implement `MediaControlClient`.
    *   Function: `sendAction(action: MediaAction)`.
    *   Use `MediaController.transportControls`:
        *   `play()`, `pause()`.
        *   `seekTo()`.
        *   `skipToNext()` / `Previous()` (or simulate seek if needed, spec says "skip-forward button that jumps ahead by 10 seconds" -> use `seekTo(current + 10000)`).

5.  **Customization Support**
    *   Read settings (Phase 6) for Color/Image preference.
    *   Apply Blur (using RenderEffect on Android 12+ or ScriptIntrinsicBlur).

6.  **Verification**
    *   Test showing the overlay over a dummy app.
    *   Verify controls work (log outputs if MediaSession not connected yet).
