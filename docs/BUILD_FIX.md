# Android build failure remediation

## Summary
CI failed on `:app:compileDebugKotlin` because several Kotlin sources referenced string
resources and Jetpack Compose UI components that were not available at build time.

## Root cause
- A recent refactor renamed or introduced new string resource identifiers in Kotlin
  code (`permission_status_pending`, `overlay_notification_action_resume`, etc.)
  without adding matching entries in `res/values/strings.xml`. The compiler therefore
  reported each identifier as an unresolved reference and flagged calls such as
  `setText(permission_action_manage)` as ambiguous because the argument was treated as
  a plain `Int` instead of a `@StringRes` value.
- `ControlsOverlay.kt` uses Material 2 composables (`Icon`, `IconButton`, `Slider`,
  `Text`), but the Gradle module only depended on the Compose BOM, Material 3, and
  icons artifacts. Without `androidx.compose.material:material` on the classpath the
  compiler could not resolve those symbols, which also cascaded into the
  "@Composable invocations can only happen" error messages.

## Fix
- Added the missing string definitions to `app/src/main/res/values/strings.xml`,
  keeping the copy consistent with the terminology already used in the UI.
- Declared the `androidx.compose.material:material` dependency alongside the existing
  Compose BOM and Material 3 artifacts so Material 2 composables resolve correctly.

## Verification
- `./gradlew clean` (optional while diagnosing)
- `./gradlew assembleDebug test`
