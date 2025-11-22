package com.polaralias.audiofocus.service

import android.view.accessibility.AccessibilityWindowInfo

internal val AccessibilityWindowInfo.isActivated: Boolean
    get() = isActive
