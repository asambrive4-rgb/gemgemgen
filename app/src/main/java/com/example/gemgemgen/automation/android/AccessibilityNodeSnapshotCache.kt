// 역할: 엔트리별 독립 TTL 및 windowId 검증으로 추가 Binder IPC(refresh) 없이 타깃 앱 메인 스레드 부하를 최소화하며 최신 접근성 노드를 캐싱합니다.
package com.example.gemgemgen.automation.android

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Short-lived node cache for specific target nodes within an active window.
 * Avoids materializing entire tree lists (.toList()) while caching frequently accessed
 * nodes (input, send, toolbar) during the 400ms settle/retry cycles.
 */
internal class AccessibilityNodeSnapshotCache(
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS
) {
    private class CacheEntry(
        val node: AccessibilityNodeInfo,
        val cachedAtMillis: Long
    )

    private var cachedWindowId: Int = -1
    private val cachedNamedNodes = mutableMapOf<String, CacheEntry>()

    fun getOrFind(
        key: String,
        root: AccessibilityNodeInfo?,
        nowMillis: Long = SystemClock.uptimeMillis(),
        find: () -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        if (root == null) {
            clear()
            return null
        }

        val windowId = runCatching { root.windowId }.getOrDefault(-1)
        if (windowId == -1 || windowId != cachedWindowId) {
            clear()
            cachedWindowId = windowId
        } else {
            val entry = cachedNamedNodes[key]
            if (entry != null) {
                val isTtlValid = nowMillis - entry.cachedAtMillis <= cacheTtlMs
                val isWindowMatching = runCatching { entry.node.windowId == windowId }.getOrDefault(false)

                if (isTtlValid && isWindowMatching) {
                    return entry.node
                } else {
                    cachedNamedNodes.remove(key)
                }
            }
        }

        val result = find()
        if (result != null) {
            cachedNamedNodes[key] = CacheEntry(result, nowMillis)
        } else {
            cachedNamedNodes.remove(key)
        }
        return result
    }

    fun invalidate(key: String) {
        cachedNamedNodes.remove(key)
    }

    fun clear() {
        cachedWindowId = -1
        cachedNamedNodes.clear()
    }

    private companion object {
        const val DEFAULT_CACHE_TTL_MS = 400L
    }
}
