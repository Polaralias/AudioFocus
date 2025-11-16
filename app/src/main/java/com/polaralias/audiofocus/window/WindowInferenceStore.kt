package com.polaralias.audiofocus.window

import java.util.concurrent.ConcurrentHashMap

object WindowInferenceStore {
    private val lastKnownPackageByWindowId = ConcurrentHashMap<Int, String>()
    @Volatile private var activeMediaPackage: String? = null

    fun rememberWindowPackage(windowId: Int, packageName: String) {
        lastKnownPackageByWindowId[windowId] = packageName
    }

    fun lastKnownPackage(windowId: Int): String? = lastKnownPackageByWindowId[windowId]

    fun retainWindowIds(observedIds: Set<Int>) {
        val iterator = lastKnownPackageByWindowId.keys.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (id !in observedIds) {
                iterator.remove()
            }
        }
    }

    fun activeMediaPackage(): String? = activeMediaPackage

    fun setActiveMediaPackage(packageName: String?) {
        activeMediaPackage = packageName
    }

    internal fun resetForTests() {
        lastKnownPackageByWindowId.clear()
        activeMediaPackage = null
    }
}
