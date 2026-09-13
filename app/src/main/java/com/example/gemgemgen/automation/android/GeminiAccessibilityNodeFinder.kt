// 역할: Gemini 앱 화면에서 텍스트 입력창, 전송 버튼, 응답 생성 완료 감지 및 새 대화 버튼 노드를 탐색합니다.
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

        return nodes()
            .firstOrNull { node ->
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                    node.isEditable
            }
    }

    fun findNodeByTextOrDescription(value: String): AccessibilityNodeInfo? {
        return nodes().firstOrNull { node -> node.matchesTextOrDescription(value) }
    }

    fun isResponseGenerating(): Boolean {
        return nodes().any { node ->
            STOP_GENERATING_DESCRIPTIONS.any { desc -> node.matchesTextOrDescription(desc) }
        }
    }

    fun hasResponseCompleted(): Boolean {
        val currentNodes = nodes()
        val hasStopButton = currentNodes.any { node ->
            STOP_GENERATING_DESCRIPTIONS.any { desc -> node.matchesTextOrDescription(desc) }
        }
        if (hasStopButton) return false

        return currentNodes.any { node ->
            RESPONSE_COMPLETED_IDLE_DESCRIPTIONS.any { desc -> node.matchesTextOrDescription(desc) }
        }
    }

    fun hasActiveChatOptionsNode(): Boolean {
        return nodes().any { node ->
            ACTIVE_CHAT_OPTIONS_DESCRIPTIONS.any { desc -> node.matchesTextOrDescription(desc) }
        }
    }

    fun findDirectNewChatNode(): AccessibilityNodeInfo? {
        // 전송 후 일반 대화방으로 UI가 전환되어 "옵션 더보기" 버튼이 나타났는지 먼저 검증합니다.
        // 아직 나타나지 않았다면 찰나의 '새 임시 채팅' 버튼 상태이므로 클릭하지 않도록 null을 반환합니다.
        if (!hasActiveChatOptionsNode()) {
            return null
        }

        return nodes().firstOrNull { node ->
            DIRECT_NEW_CHAT_DESCRIPTIONS.any { desc -> node.matchesTextOrDescription(desc) } &&
                TEMP_CHAT_EXCLUSION_KEYWORDS.none { keyword -> node.matchesTextOrDescription(keyword) }
        }
    }

    fun findNewChatNearestToSearch(): AccessibilityNodeInfo? {
        val nodes = nodes()
        val searchNode = nodes.firstOrNull { node ->
            node.matchesTextOrDescription("채팅 검색")
        } ?: return null
        val candidates = nodes.filter { node ->
            node.matchesTextOrDescription("새 채팅")
        }.mapNotNull { node ->
            val bounds = node.nodeBounds() ?: return@mapNotNull null
            node to bounds
        }
        val searchBounds = searchNode.nodeBounds() ?: return null

        return NearestNodeSelector.nearestTo(
            anchor = searchBounds,
            candidates = candidates
        ) { it.second }?.first
    }

    fun findSidebarScrollableNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val rootBounds = root.nodeBounds()
        val nodes = nodesIn(root)
        if (!nodes.hasOpenSidebarSignal(rootBounds)) return null

        return nodes.asSequence()
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

    private fun nodes(): List<AccessibilityNodeInfo> {
        return snapshotCache.getOrLoad(rootProvider()) { root -> nodesIn(root) }
    }

    private fun nodesIn(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun visit(node: AccessibilityNodeInfo) {
            if (node.isGeminiPackage()) {
                nodes += node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::visit)
            }
        }

        visit(root)
        return nodes
    }

    private fun AccessibilityNodeInfo.matchesTextOrDescription(value: String): Boolean {
        return text?.toString()?.contains(value, ignoreCase = true) == true ||
            contentDescription?.toString()?.contains(value, ignoreCase = true) == true
    }

    private fun AccessibilityNodeInfo.isGeminiPackage(): Boolean {
        return packageName?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
    }

    private fun List<AccessibilityNodeInfo>.hasOpenSidebarSignal(
        rootBounds: NodeBounds?
    ): Boolean {
        return any { node ->
            node.matchesTextOrDescription("사이드바 닫기")
        } || any { node ->
            node.matchesTextOrDescription("Gemini") &&
                node.nodeBounds()?.isLikelySidebarHeader(rootBounds) == true
        }
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
        val DIRECT_NEW_CHAT_DESCRIPTIONS = listOf("새 채팅", "새 대화", "New chat")
        val ACTIVE_CHAT_OPTIONS_DESCRIPTIONS = listOf("옵션 더보기", "More options")
        val TEMP_CHAT_EXCLUSION_KEYWORDS = listOf("임시", "temporary")
        val STOP_GENERATING_DESCRIPTIONS = listOf("중지", "응답 중지", "생성 중지", "Stop", "Stop generating")
        val RESPONSE_COMPLETED_IDLE_DESCRIPTIONS = listOf("마이크", "Open Gemini Live", "음성", "보내기", "Send")

        val GEMINI_ACCESSIBILITY_PACKAGES = setOf(
            AppDefaults.GEMINI_PACKAGE_NAME,
            AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME
        )
    }
}
