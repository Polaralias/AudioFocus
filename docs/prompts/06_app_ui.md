# Phase 6: App UI & Settings

**Role:** Android Engineer
**Skills:** `android-ui-compose`, `android-ux-flows`

## Objective
Build the user-facing application UI, including onboarding, permission handling, and settings customization.

## References
*   `../technical_specification.md` (Section 2.6 Onboarding, Section 2.4 Overlay customisation)

## Instructions

1.  **Data Persistence**
    *   Setup `Proto DataStore` or `Preferences DataStore`.
    *   Define `AppSettings` model:
        *   `isMonitoringEnabled: Boolean`
        *   `youtubeTheme: ThemeConfig`
        *   `youtubeMusicTheme: ThemeConfig`
    *   `ThemeConfig` should store: Type (Solid/Image), Color (Int), ImageUri (String), BlurLevel (Int).

2.  **Navigation**
    *   Setup Navigation Compose.
    *   Routes: `Onboarding`, `Home`, `Settings`.

3.  **Onboarding Flow**
    *   Create a step-by-step wizard if permissions are missing.
    *   **Screens:**
        *   Welcome & Value Prop.
        *   Overlay Permission Request.
        *   Accessibility Permission Request (Explain why).
        *   Notification Permission Request.
    *   Use `LifecycleEventObserver` to detect when user returns from Settings to auto-advance.

4.  **Home / Settings Screen**
    *   **Master Toggle:** Enable/Disable `AudioFocusService`.
    *   **Status Indicators:** Show if permissions are active.
    *   **Theme Customization:**
        *   Color Picker (HSV or Material Grid).
        *   Image Picker (System Photo Picker).
        *   Blur Slider (0-3).
        *   Preview View (Show a mock overlay).

5.  **Service Integration**
    *   Observe `isMonitoringEnabled` flow in `AudioFocusService`.
    *   Start/Stop logic based on the toggle.

6.  **Verification**
    *   Test the full flow: Install -> Onboarding -> Grant Permissions -> Enable Service.
    *   Verify settings changes reflect in the Overlay (mocked if needed).
