# AudioFocus Implementation Plan

This directory contains a series of detailed prompts to build the AudioFocus Android application. Each file represents a phase of the development process.

## Phases

1.  **[Project Setup](01_setup.md)**
    - Initialize the project, dependencies, and core data models.
2.  **[Service & Permissions](02_service_permissions.md)**
    - Implement the Foreground Service and handle critical permissions (Overlay, Accessibility, Notification).
3.  **[Monitors](03_monitors.md)**
    - Build the monitoring components (Accessibility, MediaSession, Notification, ForegroundApp).
4.  **[Logic Engine](04_logic_engine.md)**
    - Implement the `PlaybackStateEngine` and decision logic.
5.  **[Overlay UI](05_overlay_ui.md)**
    - Create the system alert window overlay, media controls, and customization.
6.  **[App UI & Settings](06_app_ui.md)**
    - Build the onboarding flow, settings screen, and persistence.
7.  **[QA & Polish](07_qa.md)**
    - Verify requirements, optimize performance, and handle edge cases.

## Instructions for the Agent

*   Follow the phases in order.
*   Refer to `../technical_specification.md` for the single source of truth regarding requirements.
*   Adhere to the skills defined in `../../AGENTS.md`, specifically:
    *   `android-dev-standards`
    *   `android-engineering-core`
    *   `android-ui-compose`
