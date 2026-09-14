// 역할: Gemini 앱 화면에서 텍스트 입력창, 전송 버튼, 옵션 더보기와 연계된 새 대화 버튼 노드를 지연 평가 방식으로 탐색합니다.
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

    fun findInputNode(): AccessibilityNodeInfo? {
        findNodeByViewId(inputResourceId)?.let { return it }

        return nodesSequence().firstOrNull { node ->
            node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                node.isEditable
        }
    }

    fun findNodeByTextOrDescription(value: String): AccessibilityNodeInfo? {
        return nodesSequence().firstOrNull { node -> node.matchesTextOrDescription(value) }
    }

    fun hasMoreOptions(): Boolean {
        return nodesSequence().any { it.matchesTextOrDescription(MORE_OPTIONS_DESCRIPTION) }
    }

    fun findNewChatWithMoreOptions(): AccessibilityNodeInfo? {
        if (!hasMoreOptions()) return null
        return nodesSequence().firstOrNull { it.matchesTextOrDescription(NEW_CHAT_DESCRIPTION) }
    }

    fun findNewChatNearestToSearch(): AccessibilityNodeInfo? {
        val searchNode = nodesSequence().firstOrNull { node ->
            node.matchesTextOrDescription("채팅 검색")
        } ?: return null
        val searchBounds = searchNode.nodeBounds() ?: return null

        val candidates = nodesSequence()
            .filter { node -> node.matchesTextOrDescription("새 채팅") }
            .mapNotNull { node ->
                val bounds = node.nodeBounds() ?: return@mapNotNull null
                node to bounds
            }
            .toList()

        return NearestNodeSelector.nearestTo(
            anchor = searchBounds,
            candidates = candidates
        ) { it.second }?.first
    }

    fun findSidebarScrollableNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val rootBounds = root.nodeBounds()
        val hasSidebarSignal = nodesSequence().any { node ->
            node.matchesTextOrDescription("사이드바 닫기") ||
                (node.matchesTextOrDescription("Gemini") &&
                    node.nodeBounds()?.isLikelySidebarHeader(rootBounds) == true)
        }
        if (!hasSidebarSignal) return null

        return nodesSequence()
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

    private fun nodesSequence(): Sequence<AccessibilityNodeInfo> {
        return AccessibilityNodeTraversal.lazyTraverse(rootProvider()) { pkg ->
            pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
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
