# Phase 2: Service & Permissions

**Role:** Android Engineer
**Skill:** `android-engineering-core`

## Objective
Implement the core Android Services required for the application and the permission handling infrastructure.

## References
*   `../technical_specification.md` (Section 2.5 App lifecycle, Section 2.6 Onboarding, Section 4.1 Component overview)

## Instructions

1.  **Service Implementation**
    *   **Main Foreground Service (`AudioFocusService`):**
        *   Create a `Service` class that runs as a Foreground Service.
        *   Implement `startForeground` with a persistent notification (channel `monitor_service`).
        *   Handle `START_STICKY` to ensure recovery.
        *   Implement a method to `stopMonitoring()` which stops the service.
    *   **Accessibility Service (`AppAccessibilityService`):**
        *   Extend `AccessibilityService`.
        *   Configure `accessibility_service_config.xml`:
            *   `accessibilityFeedbackType="feedbackGeneric"`
            *   `accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagRequestEnhancedWebAccessibility"` (Adjust as needed for view inspection).
            *   `canRetrieveWindowContent="true"`.
        *   *Note:* The logic to parse events will be added in Phase 3. For now, set up the lifecycle and binding.
    *   **Notification Listener (`AppNotificationListenerService`):**
        *   Extend `NotificationListenerService`.
        *   Register in Manifest with `BIND_NOTIFICATION_LISTENER_SERVICE`.

2.  **Manifest Configuration**
    *   Declare all services in `AndroidManifest.xml`.
    *   Add permissions:
        *   `FOREGROUND_SERVICE`
        *   `FOREGROUND_SERVICE_SPECIAL_USE` (if target SDK 34+) or appropriate type.
        *   `SYSTEM_ALERT_WINDOW`
        *   `PACKAGE_USAGE_STATS` (declare `ignore` protection level if needed or just `uses-permission`).
        *   `QUERY_ALL_PACKAGES` (Required to detect target apps package presence).

3.  **Permission Management**
    *   Create a `PermissionManager` (or `PermissionRepository`) in `core` or `domain`.
    *   Implement checks for:
        *   `Settings.canDrawOverlays(context)`
        *   Accessibility Service enabled (check `AccessibilityManager.getEnabledAccessibilityServiceList`).
        *   Notification Listener enabled (check `NotificationManagerCompat.getEnabledListenerPackages`).
        *   Usage Stats (check `AppOpsManager`).
    *   Implement Intent factories to open the respective System Settings pages for the user to enable these permissions.

4.  **Verification**
    *   Deploy the app.
    *   Manually invoke the start of `AudioFocusService` (e.g., via a temp button or `adb`) and verify the notification appears.
    *   Verify the services are registered in the system (using `adb shell dumpsys`).
