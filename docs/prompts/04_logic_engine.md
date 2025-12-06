# Phase 4: Logic Engine

**Role:** Android Engineer
**Skill:** `android-engineering-core`

## Objective
Implement the `PlaybackStateEngine` which aggregates signals from monitors and makes the decision to show or hide the overlay.

## References
*   `../technical_specification.md` (Section 2.1 Overlay behaviour matrix, Section 4.2 Data Model)

## Instructions

1.  **PlaybackStateEngine**
    *   Create `PlaybackStateEngine` class (Singleton or Scoped).
    *   **Inputs:**
        *   `accessibilityState: Flow<AccessibilityState>`
        *   `mediaSessionState: Flow<MediaSessionState>`
        *   `foregroundApp: Flow<String>`
    *   **Output:**
        *   `overlayDecision: Flow<OverlayDecision>`

2.  **Logic Implementation**
    *   Implement the Behaviour Matrix from Section 2.1 exactly.
    *   **Rules:**
        *   **YouTube:**
            *   Show Overlay IF (Playback=Playing AND (Window=Fullscreen OR PiP OR Minimised) AND Type=Video).
            *   Hide IF (Playback=Paused/Stopped) OR (Window=Background AND Type=Audio).
        *   **YouTube Music:**
            *   Show Overlay IF (Playback=Playing AND Type=Video).
            *   Hide IF (Type=Audio) OR (Playback=Paused).
    *   **Playback Type Detection:**
        *   Distinguish Video vs Audio-only.
        *   *Hint:* YouTube Music often separates Video vs Song mode. Check Accessibility nodes for "Song" vs "Video" toggles or specific view hierarchies if possible, or rely on the presence of a video surface.
    *   **Debouncing:**
        *   Apply `debounce` or distinctUntilChanged to avoid rapid flickering of the overlay during transitions.

3.  **Unit Testing**
    *   Write comprehensive unit tests for `PlaybackStateEngine`.
    *   Mock the inputs.
    *   Verify `OverlayDecision` matches the matrix for all permutations.

4.  **Verification**
    *   Run the tests.
    *   Verify logic handles "No Session" or "App Closed" states correctly (Overlay should be NONE).
