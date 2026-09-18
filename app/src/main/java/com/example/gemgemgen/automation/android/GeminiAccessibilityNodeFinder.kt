// 역할: 시스템 네이티브 인덱스 텍스트/ID 검색 우선 및 단기 스냅샷 캐시를 통해 Binder IPC와 메모리 부하를 최소화하며 Gemini 화면 노드를 탐색합니다.
package com.example.gemgemgen.automation.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class GeminiAccessibilityNodeFinder(
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val inputResourceId: String
) {
    private val snapshotCache = AccessibilityNodeSnapshotCache()

    fun invalidateCache() {
        snapshotCache.clear()
    }

    fun getPerformanceStats(): String {
        return "CacheHits=${snapshotCache.cacheHitCount}, CacheMisses=${snapshotCache.cacheMissCount}, IPC_GetChild=${AccessibilityNodeTraversal.totalGetChildCalls}, PrunedSystemTrees=${AccessibilityNodeTraversal.totalPrunedSubtrees}"
    }

    fun resetPerformanceStats() {
        snapshotCache.resetStats()
        AccessibilityNodeTraversal.resetStats()
    }

    fun findInputNode(): AccessibilityNodeInfo? {
        findNodeByViewId(inputResourceId)?.let { return it }

        findNodesByText("프롬프트").firstOrNull { it.isInputLike() }?.let { return it }

        return cachedNodes().firstOrNull { node -> node.isInputLike() }
    }

    fun findNodeByTextOrDescription(value: String): AccessibilityNodeInfo? {
        findNodesByText(value).firstOrNull { it.matchesTextOrDescription(value) }?.let { return it }

        val root = rootProvider() ?: return null
        return AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
            pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
        }.firstOrNull { node -> node.matchesTextOrDescription(value) }
    }

    fun hasMoreOptions(): Boolean {
        return findNodesByText(MORE_OPTIONS_DESCRIPTION).any { it.matchesTextOrDescription(MORE_OPTIONS_DESCRIPTION) }
    }

    fun findNewChatWithMoreOptions(): AccessibilityNodeInfo? {
        if (!hasMoreOptions()) return null
        return findNodeByTextOrDescription(NEW_CHAT_DESCRIPTION)
    }

    fun findNewChatNearestToSearch(): AccessibilityNodeInfo? {
        val searchNode = findNodeByTextOrDescription("채팅 검색") ?: return null
        val searchBounds = searchNode.nodeBounds() ?: return null

        val nativeCandidates = findNodesByText("새 채팅")
        val candidateSource = if (nativeCandidates.isNotEmpty()) nativeCandidates else cachedNodes()
        val candidates = candidateSource
            .filter { node -> node.matchesTextOrDescription("새 채팅") }
            .mapNotNull { node ->
                val bounds = node.nodeBounds() ?: return@mapNotNull null
                node to bounds
            }

        return NearestNodeSelector.nearestTo(
            anchor = searchBounds,
            candidates = candidates
        ) { it.second }?.first
    }

    fun findSidebarScrollableNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val rootBounds = root.nodeBounds()
        val allNodes = cachedNodes()
        val hasSidebarSignal = allNodes.any { node ->
            node.matchesTextOrDescription("사이드바 닫기") ||
                (node.matchesTextOrDescription("Gemini") &&
                    node.nodeBounds()?.isLikelySidebarHeader(rootBounds) == true)
        }
        if (!hasSidebarSignal) return null

        return allNodes
            .filter { node -> node.isScrollable }
            .mapNotNull { node ->
                val bounds = node.nodeBounds() ?: return@mapNotNull null
                node to bounds
            }
            .filter { (_, bounds) ->
                bounds.isLikelySidebarScrollable(rootBounds = rootBounds)
            }
            .maxByOrNull { (_, bounds) -> bounds.area }
            ?.first
    }

    private fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        return try {
            rootProvider()
                ?.findAccessibilityNodeInfosByViewId(viewId)
                ?.firstOrNull { node -> node.isGeminiPackage() }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun findNodesByText(value: String): List<AccessibilityNodeInfo> {
        return try {
            rootProvider()
                ?.findAccessibilityNodeInfosByText(value)
                ?.filter { it.isGeminiPackage() }
                .orEmpty()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun AccessibilityNodeInfo.isInputLike(): Boolean {
        return isEditable || className?.contains("EditText", ignoreCase = true) == true
    }

    private fun cachedNodes(): List<AccessibilityNodeInfo> {
        return snapshotCache.getOrLoad {
            val root = rootProvider() ?: return@getOrLoad emptyList()
            AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
                pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
            }.toList()
        }
    }

    private fun AccessibilityNodeInfo.matchesTextOrDescription(value: String): Boolean {
        return text?.toString()?.contains(value, ignoreCase = true) == true ||
            contentDescription?.toString()?.contains(value, ignoreCase = true) == true
    }

    private fun AccessibilityNodeInfo.isGeminiPackage(): Boolean {
        return packageName?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
    }

    private fun AccessibilityNodeInfo.nodeBounds(): NodeBounds? {
        val rect = Rect()
        getBoundsInScreen(rect)
        if (rect.isEmpty) return null

        return NodeBounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )
    }

    private fun NodeBounds.isLikelySidebarScrollable(
        rootBounds: NodeBounds?
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        if (rootBounds == null) return true

        val tallEnoughForSidebarList = height >= rootBounds.height / 3
        val startsNearLeftEdge = left <= rootBounds.left + rootBounds.width / 4
        val centerIsNotOnRightPane = centerX <= rootBounds.centerX

        return tallEnoughForSidebarList && startsNearLeftEdge && centerIsNotOnRightPane
    }

    private fun NodeBounds.isLikelySidebarHeader(rootBounds: NodeBounds?): Boolean {
        if (rootBounds == null) return true

        return top <= rootBounds.top + rootBounds.height / 8 &&
            left <= rootBounds.left + rootBounds.width / 2 &&
            centerX <= rootBounds.centerX
    }

    private val NodeBounds.width: Int
        get() = right - left

    private val NodeBounds.height: Int
        get() = bottom - top

    private val NodeBounds.area: Long
        get() = width.toLong() * height.toLong()

    internal companion object {
        const val NEW_CHAT_DESCRIPTION = "새 채팅"
        const val MORE_OPTIONS_DESCRIPTION = "옵션 더보기"

        val GEMINI_ACCESSIBILITY_PACKAGES = setOf(
            AppDefaults.GEMINI_PACKAGE_NAME,
            AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME
        )
    }
}
