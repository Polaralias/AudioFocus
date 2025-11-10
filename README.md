# AudioFocus

An Android application that provides media transport control overlays for YouTube and YouTube Music video playback.

## Features

- **Overlay Controls**: Display media controls (play/pause, seek) over video playback
- **Smart Detection**: Automatically detects when YouTube/YouTube Music videos are playing
- **Accessibility Service**: Monitors window states to determine fullscreen/minimized modes
- **Notification Integration**: Media session monitoring for playback state
- **Customizable**: Per-app enable/disable controls

## Requirements

- Android API 31+ (Android 12+)
- Permissions:
  - Draw over other apps (for overlay)
  - Notification access (for media session monitoring)
  - Accessibility service (for window detection)

## Architecture

The app uses a clean architecture with the following layers:

### Services
- **OverlayService**: Manages overlay display and media controls
- **MediaNotificationListener**: Monitors notification listener status
- **AccessWindowsService**: Accessibility service for window information

### State Management
- **FocusStateRepository**: Central state management using Kotlin Flow
- **OverlayManager**: Policy engine for overlay show/hide decisions
- **MediaSessionMonitor**: Monitors active media sessions

### UI
- **SettingsActivity**: Main settings screen (Jetpack Compose)
- **ControlsOverlay**: Media transport controls overlay (Jetpack Compose)

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + XML layouts
- **Build System**: Gradle with Kotlin DSL
- **Architecture Components**: ViewModel, Flow, DataStore
- **Dependencies**: See `app/build.gradle.kts`

## Building

```bash
./gradlew assembleDebug
```

## Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

## Manual QA checklist

Use a device or emulator with YouTube installed to verify the settings status banner:

1. Launch AudioFocus and grant overlay, notification listener, and accessibility permissions. Start playback in YouTube and confirm the settings screen shows “Overlay service running”.
2. Pause or stop playback while keeping the service running. Return to settings and verify the message changes to “Overlay service waiting for playback”.
3. Revoke the app’s notification permission or stop the overlay service via the notification. Reopen settings and ensure the banner shows the error text reported by the service (for example, the notification warning) until the issue is resolved.

## Project Status

✅ All critical issues have been resolved
✅ Code quality reviewed and approved
✅ Security reviewed and approved
✅ Ready for build and deployment

See [docs/CODE_REVIEW.md](docs/CODE_REVIEW.md) for detailed review findings.
See [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) for security assessment.

## License

[Add license information]

## Contributing

[Add contribution guidelines]
