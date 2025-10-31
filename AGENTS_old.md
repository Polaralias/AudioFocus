# AudioFocus Agent Guidelines

This document captures the product vision and technical constraints for the **AudioFocus** Android overlay service. Future automated contributions should align with the requirements below. The package name is **com.polaralias.audiofocus**.

## Vision & Product Goals
- Provide a distraction-reducing overlay while users watch video content in the official YouTube or YouTube Music apps.
- Maintain a "slick and stable" user experience with smooth fade transitions and minimal battery impact.
- Run continuously in the background with minimal required permissions, while remaining transparent to the user (foreground notification, clear onboarding).

## Supported Apps & Behaviors
Only two packages are in scope:
- `com.google.android.youtube`
- `com.google.android.apps.youtube.music`

Overlay policy matrix:
| App | Window state | Playback type | Overlay behavior |
| --- | --- | --- | --- |
| YouTube | Fullscreen, minimized, or PiP | Video playback (`STATE_PLAYING`) | Full-screen overlay. |
| YouTube | Any | Paused/stopped/background | No overlay. |
| YouTube Music | Fullscreen video | Video playback | Full-screen overlay. |
| YouTube Music | Miniplayer or non-fullscreen video | Video playback | Partial overlay covering ~5/6 of screen height; must allow touch pass-through. |
| YouTube Music | Any | Audio-only playback or background | No overlay. |

## Core Components
1. **Foreground Service**
   - Hosts the overlay via `WindowManager` using `TYPE_APPLICATION_OVERLAY` windows.
   - Keeps persistent notification; service type should match Android 13/14 requirements (e.g., `mediaProjection`/`specialUse`).
   - Animates overlay fade in/out with property animators (~200 ms). Release views when hidden to minimize GPU usage.
2. **Accessibility Service**
   - Request user enablement; subscribe to `TYPE_WINDOWS_CHANGED` and `TYPE_WINDOW_STATE_CHANGED` events.
   - Track foreground window, PiP transitions, and distinguish YouTube UI states (fullscreen, mini-player, PiP) by package, class names (`WatchWhileActivity`, etc.), and node bounds.
   - Maintain debounced state machine (~200–300 ms) to prevent flicker.
3. **Notification Listener + MediaSession**
   - Limit to the two packages via `NotificationListenerService` filtering.
   - Build `MediaController` instances from notifications to read `PlaybackState` and `MediaMetadata`.
   - Heuristics:
     - YouTube video detection: treat any `STATE_PLAYING` session as video.
     - YouTube Music video detection: check `METADATA_KEY_VIDEO_HEIGHT/VIDEO_WIDTH` or notification extras `android.mediaMetadata.PRESENTATION_DISPLAY_TYPE == 1`; fall back to audio-only if metadata absent.
   - Cache session metadata briefly to avoid redundant work.
4. **Overlay Manager**
   - Combine signals (foreground app + playback state + window mode) into deterministic overlay commands.
   - Support full-screen and partial overlay layouts; partial overlay must set `FLAG_NOT_TOUCHABLE` for passthrough.
   - Provide manual pause toggle via notification action.

## Permissions & Onboarding
- Required permissions: `SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE`, `BIND_NOTIFICATION_LISTENER_SERVICE`, `FOREGROUND_SERVICE` (with appropriate type on newer Android versions).
- On first launch, guide user through granting overlay, accessibility, and notification listener access sequentially with clear rationale.

## Performance & Reliability
- Avoid polling; react to callbacks only.
- Debounce state changes and clean up overlays when hidden.
- Log state transitions for debugging; consider remote-configurable heuristics for resilience against upstream UI changes.
- Prepare for Google updates: encapsulate view ID/class heuristics to simplify maintenance.

## Testing Expectations
- Unit/integration tests for the overlay manager state machine (simulate combinations of playback and window states).
- Instrumentation tests (or manual QA scripts) to validate fade animations, touch passthrough, and permission flows.
- Document testing steps in PR descriptions when manual verification is performed.

## Out of Scope / Future Work
- Support for additional media apps beyond YouTube and YouTube Music.
- Advanced heuristics for non-official clients (e.g., NewPipe) unless requirements change.
- Attempting to distinguish content types without reliable metadata (e.g., podcasts vs videos in YouTube Music audio-only mode).

