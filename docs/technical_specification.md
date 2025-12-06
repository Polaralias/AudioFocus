# AudioFocus – Extended Android Technical Specification

## 1. Product overview

**Product name:** AudioFocus
**Platforms:** Android smartphones and tablets across diverse manufacturers and screen sizes
**Core purpose:** AudioFocus is designed to help users maintain concentration by masking distracting on-screen video content within selected media apps, while still allowing audio playback and basic media control operations. The system places a robust, configurable full-screen overlay over specific target applications when conditions indicate that video content is being shown. Audio-only playback never triggers an overlay. The user can continue to hear content and interact with media controls without being exposed to visual material that might divert their attention.

Supported target applications at launch include:

* **YouTube** (`com.google.android.youtube`) – recognised through package inspection and media session identifiers.
* **YouTube Music** (`com.google.android.apps.youtube.music`) – similarly detected via package name, accessibility events and media session callbacks.

AudioFocus dynamically responds to both playback state and the visual window conditions of these apps, producing context-aware overlay behaviour that adapts instantly as users navigate between screens, switch apps, enter PiP mode or pause playback.

The application enforces strict permission and window-state handling to ensure overlays never appear over system user interfaces. System callouts, notification shade interactions, permission prompts and lock screen surfaces must remain unobstructed to maintain usability and platform compliance.

## 2. Functional requirements

### 2.1 Overlay behaviour matrix

The overlay engine must follow these behavioural rules exactly:

| App           | Window state                         | Playback type              | Overlay behaviour   |
| :------------ | :----------------------------------- | :------------------------- | :------------------ |
| YouTube       | Fullscreen, minimised in-app, or PiP | Visible video playback     | Full-screen overlay |
| YouTube       | Any                                  | Paused, stopped            | No overlay          |
| YouTube       | Background                           | Non-visible video playback | No overlay          |
| YouTube Music | Fullscreen video                     | Video playback             | Full-screen overlay |
| YouTube Music | Any visible window state             | Video playback             | Full-screen overlay |
| YouTube Music | Background                           | Background playback        | No overlay          |

Additional behavioural constraints:

* Overlays must never obstruct any system-layer UI such as the notification shade, global volume sliders, emergency alerts, lock screen affordances or Android settings screens.
* Overlays must only respond to the explicitly supported target applications. Other apps with embedded video players, custom media stacks or third-party playback engines must be ignored until future expansions.
* The system must be capable of recovering quickly when multiple apps are providing active media sessions. Only the topmost supported app with visible video should trigger an overlay.

### 2.2 Playback detection requirements

Overlay decisions depend on precise and reliable detection of:

* **Media session state** (playing, paused or stopped).
* **Playback type** (video vs audio-only).
* **Window visibility** (visible fullscreen, visible non-fullscreen, PiP, background or not visible).

As Android does not expose a unified API to determine whether a media session corresponds to visible video content, the solution must combine several approaches:

* **Accessibility tree traversal** to detect whether a video surface (SurfaceView or TextureView) is present within the window.
* **MediaSessionManager / MediaController** readings for accurate playback state and metadata.
* **NotificationListenerService** to cross-check that the app considers its media session active and foreground-relevant.
* **UsageStatsManager or ActivityManager** for fallback foreground app estimation when accessibility data becomes incomplete.

Each signal must be incorporated into the overall decision engine so that missing or stale information does not lead to false overlays.

### 2.3 Media controls

The overlay includes a compact but responsive cluster of media controls that must remain usable on all screen sizes:

* A scrubbable progress bar enabling the user to seek within the currently playing content.
* A skip-forward button that jumps ahead by 10 seconds.
* A skip-back button that rewinds by 10 seconds.
* A central play/pause toggle.

Control requirements:

* Controls must update in near real time and accurately reflect playback state and position.
* Controls must be disabled when the connected media session does not advertise the required transport actions.
* All control actions must be forwarded via the correct `MediaController.TransportControls` methods or media key dispatch when necessary.
* Controls must remain available even when the full-screen overlay is active, allowing uninterrupted audio-focused consumption.

### 2.4 Overlay customisation

The customisation system offers users the ability to select a visual style that fully obscures background content while providing personalisation options:

* Users may choose a **solid colour** overlay, selected via an HSV or Material Design palette, along with a hex entry for precise colour selection.
* Alternatively, users may provide a **photo or artwork** from their device gallery. Images should support scaling modes including centre crop, fit-to-screen and centred placement.
* An optional blur setting (levels 0–3) is available for images, helping users soften visual textures.
* Opacity must always remain fixed at complete coverage. Even when colours or images are customised, no settings should allow video content to leak through or become partially visible.
* Different overlay appearances may be saved per app, allowing separate themes for YouTube and YouTube Music.

### 2.5 App lifecycle and foreground service

AudioFocus operates primarily through a long-running foreground service. The service must:

* Maintain access to overlay permissions and ensure overlays remain visible when necessary.
* React immediately to accessibility events, media session updates, app switches and configuration changes.
* Recover gracefully after OS-initiated process termination. If relaunched by the system, it must restore overlay state according to the last known active session.
* Respect energy efficiency requirements and avoid waking the device unnecessarily.

Users must be able to toggle monitoring through the application’s main interface. The service must suspend all processing immediately when monitoring is disabled.

### 2.6 Onboarding and permission handling

AudioFocus requires several high-privilege capabilities. Onboarding must:

* Clearly describe why overlay permissions are required and guide the user through enabling them.
* Request accessibility service activation using official Android guidance screens.
* Explain why notification access is needed for media session discovery.
* Provide an optional explanation for usage stats access when device-specific behaviour makes it helpful.

All permission requests should provide fallback instructions for devices that alter or restrict standard system menus.

## 3. Non-functional requirements

### Performance

The overlay must render smoothly across all supported devices. AudioFocus should:

* Maintain consistent frame rates while overlays are displayed. Lag or flicker is not acceptable.
* Minimise CPU cost associated with view hierarchy parsing by caching window states where possible.
* Avoid polling loops in favour of event-driven updates.

### Battery and resource usage

AudioFocus must behave responsibly in long-running sessions:

* Foreground service should avoid frequent wake locks.
* Accessibility event processing must be optimised to short bursts.
* Media session listeners must disconnect cleanly when no sessions are present.

### Privacy

All data stays on-device. The application must not:

* Upload accessibility information.
* Persist any content identifiers or sensitive metadata.
* Record video frames or extract visible content.

### Compatibility

The system should support a broad range of Android versions (API 26+) and devices, including varied aspect ratios, folding screens and tablets. Vendor-specific behaviours must be handled gracefully.

## 4. System architecture

### 4.1 Component overview

AudioFocus consists of several coordinated subsystems:

1. **Main UI module** handling onboarding, settings, and visual configuration.
2. **OverlayManager**, which creates and controls overlay windows and applies appearance rules.
3. **PlaybackStateEngine**, responsible for merging signals to produce the authoritative overlay decision.
4. **MediaSessionMonitor**, which subscribes to media sessions.
5. **AccessibilityMonitor**, detecting visibility of windows and video surfaces.
6. **NotificationMonitor**, capturing additional playback cues.
7. **ForegroundAppDetector**, determining which app is currently frontmost.
8. **MediaControlClient**, forwarding user commands to active media sessions.
9. **Settings and Storage** providing persistence.

These components must operate asynchronously but cohesively, sharing results through lightweight data objects and callbacks.

### 4.2 Data model

```kotlin
enum class TargetApp(val packageName: String) {
    YOUTUBE("com.google.android.youtube"),
    YOUTUBE_MUSIC("com.google.android.apps.youtube.music")
}

enum class WindowState {
    NOT_VISIBLE,
    FOREGROUND_FULLSCREEN,
    FOREGROUND_MINIMISED,
    PICTURE_IN_PICTURE,
    BACKGROUND
}

enum class PlaybackType {
    NONE,
    AUDIO_ONLY,
    VISIBLE_VIDEO
}

enum class PlaybackStateSimplified {
    STOPPED,
    PAUSED,
    PLAYING
}

enum class OverlayMode {
    NONE,
    FULL_SCREEN
}

data class AppVisualState(
    val app: TargetApp,
    val windowState: WindowState,
    val playbackState: PlaybackStateSimplified,
    val playbackType: PlaybackType
)

data class OverlayDecision(

```
