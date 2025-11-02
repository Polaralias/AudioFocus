package com.polaralias.audiofocus.service

import android.view.accessibility.AccessibilityWindowInfo

/**
 * Temporary compatibility shim for legacy references to [AccessibilityWindowInfo.isActive].
 *
 * Older code paths in the accessibility pipeline still call `isActivated`, mirroring the
 * equivalent view API. The framework surface never exposed such a method, so the build fails
 * once the Kotlin compiler processes those calls. Translating the call to the modern
 * [AccessibilityWindowInfo.isActive] accessor keeps the semantics intact while unblocking the
 * build.
 */
internal val AccessibilityWindowInfo.isActivated: Boolean
    get() = isActive
