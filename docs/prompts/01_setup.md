# Phase 1: Project Setup & Infrastructure

**Role:** Android Engineer
**Skill:** `android-dev-standards`

## Objective
Initialize the AudioFocus Android project, set up the build environment, and define the core data models as per the technical specification.

## References
*   `../technical_specification.md` (Section 4.2 Data Model, Section 3 Compatibility)

## Instructions

1.  **Project Initialization**
    *   Create a new Android project.
    *   **Min SDK:** 26 (Android 8.0) as per spec.
    *   **Language:** Kotlin.
    *   **UI Toolkit:** Jetpack Compose (Material 3).
    *   **Architecture:** MVVM with Clean Architecture principles.

2.  **Dependencies**
    *   Add necessary dependencies in `libs.versions.toml` (if using Version Catalogs) or `build.gradle.kts`.
    *   **DI:** Hilt.
    *   **Async:** Coroutines & Flow.
    *   **Persistence:** DataStore (for settings), Room (if needed for complex data, though DataStore might suffice for simple prefs, check spec). Spec mentions "Settings and Storage providing persistence".
    *   **Navigation:** Jetpack Navigation Compose.
    *   **Media:** `androidx.media` or `media3` (ensure compatibility with `MediaSessionManager` and `NotificationListenerService`).
    *   **Serialization:** Kotlin Serialization.

3.  **Project Structure**
    *   Set up a modular or clean package structure:
        *   `com.audiofocus.app`
        *   `core`: Common models, utils.
        *   `data`: Repositories, data sources.
        *   `domain`: Use cases, business logic.
        *   `ui`: Composable screens, themes.
        *   `service`: Foreground service components.

4.  **Core Data Models**
    *   Implement the Enums and Data Classes defined in Section 4.2 of the spec in a `core/model` module or package.
    *   **Note:** The `OverlayDecision` class in the spec might be incomplete. Implement it as follows based on context:
        ```kotlin
        data class OverlayDecision(
            val shouldOverlay: Boolean,
            val overlayMode: OverlayMode,
            val targetApp: TargetApp?
        )
        ```
    *   Ensure all Enums (`TargetApp`, `WindowState`, `PlaybackType`, etc.) are correctly defined.

5.  **Verification**
    *   Ensure the project builds successfully.
    *   Verify all dependencies are resolved.
