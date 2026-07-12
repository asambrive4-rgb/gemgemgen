package com.example.gemgemgen.automation.android

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Short-lived node list for one root. Clears previous list on rebuild/miss so snapshots
 * do not linger after the cache window or a hierarchy change.
 */
internal class AccessibilityNodeSnapshotCache(
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS
) {
    private var cachedRoot: AccessibilityNodeInfo? = null
    private var cachedNodes: List<AccessibilityNodeInfo> = emptyList()
    private var cachedAtMillis: Long = 0L

    fun getOrLoad(
        root: AccessibilityNodeInfo?,
        nowMillis: Long = SystemClock.uptimeMillis(),
        load: (AccessibilityNodeInfo) -> List<AccessibilityNodeInfo>
    ): List<AccessibilityNodeInfo> {
        if (root == null) {
            clear()
            return emptyList()
        }
        if (root === cachedRoot && nowMillis - cachedAtMillis <= cacheTtlMs) {
            return cachedNodes
        }

        clear()
        val nodes = load(root)
        cachedRoot = root
        cachedNodes = nodes
        cachedAtMillis = nowMillis
        return nodes
    }

    fun clear() {
        cachedRoot = null
        cachedNodes = emptyList()
        cachedAtMillis = 0L
    }

    private companion object {
        // Covers multi-find within one step; retry delays (250ms+) usually rebuild.
        const val DEFAULT_CACHE_TTL_MS = 200L
    }
}
