// 역할: 접근성 서비스의 화면 노드 검색 속도를 높이기 위해 노드 스냅샷을 캐시합니다.
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

    var cacheHitCount = 0L
        private set
    var cacheMissCount = 0L
        private set

    fun resetStats() {
        cacheHitCount = 0L
        cacheMissCount = 0L
    }

    fun getOrLoad(
        root: AccessibilityNodeInfo?,
        nowMillis: Long = SystemClock.uptimeMillis(),
        load: (AccessibilityNodeInfo) -> List<AccessibilityNodeInfo>
    ): List<AccessibilityNodeInfo> {
        if (root == null) {
            clear()
            return emptyList()
        }
        if (cachedNodes.isNotEmpty() && (root == cachedRoot || root === cachedRoot) && nowMillis - cachedAtMillis <= cacheTtlMs) {
            cacheHitCount++
            return cachedNodes
        }

        clear()
        cacheMissCount++
        val nodes = load(root)
        cachedRoot = root
        cachedNodes = nodes
        cachedAtMillis = nowMillis
        return nodes
    }

    fun getOrLoad(
        nowMillis: Long = SystemClock.uptimeMillis(),
        load: () -> List<AccessibilityNodeInfo>
    ): List<AccessibilityNodeInfo> {
        if (cachedNodes.isNotEmpty() && nowMillis - cachedAtMillis <= cacheTtlMs) {
            cacheHitCount++
            return cachedNodes
        }

        clear()
        cacheMissCount++
        val nodes = load()
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
        // Covers multi-find within one step and retry intervals (250ms).
        const val DEFAULT_CACHE_TTL_MS = 400L
    }
}
