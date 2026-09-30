// 역할: 공통 NodeFinder 기반 위에서 Gemini 전용 ViewId·사이드바·근접 새 채팅 노드를 탐색합니다.
package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class GeminiAccessibilityNodeFinder(
    rootProvider: () -> AccessibilityNodeInfo?,
    private val inputResourceId: String
) : BaseAccessibilityNodeFinder(rootProvider, GEMINI_ACCESSIBILITY_PACKAGES) {
    private val inputViewIds = listOf(
        inputResourceId,
        "com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_text"
    ).distinct()

    fun findInputNode(): AccessibilityNodeInfo? =
        findInputNodeBy(viewIds = inputViewIds, keywords = INPUT_KEYWORDS)

    fun findSendNode(): AccessibilityNodeInfo? =
        cachedNode("send") { root ->
            for (viewId in SEND_VIEW_IDS) {
                findNodeByViewId(root, viewId)?.let { return@cachedNode it }
            }
            findFirstNodeByExactCandidates(root, SEND_KEYWORDS) { node, keyword ->
                node.matchesTextOrDescription(keyword)
            }
        }

    fun findNodeByTextOrDescription(value: String): AccessibilityNodeInfo? =
        cachedNode("text_$value") { root ->
            findFirstNodeContainingTextOrDescription(root, value)
        }

    fun findNewChatNearestToSearch(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val searchNode = findNodeByTextOrDescription("채팅 검색") ?: return null
        val searchBounds = searchNode.nodeBounds() ?: return null

        val nativeCandidates = findNodesByText(root, NEW_CHAT_DESCRIPTION)
        val candidateSequence = if (nativeCandidates.isNotEmpty()) {
            nativeCandidates.asSequence()
        } else {
            traverseTargetPackage(root)
        }
        val candidateBoundsSequence = candidateSequence
            .filter { it.matchesTextOrDescription(NEW_CHAT_DESCRIPTION) }
            .mapNotNull { node -> node.nodeBounds()?.let { node to it } }

        return NearestNodeSelector.nearestTo(
            anchor = searchBounds,
            candidates = candidateBoundsSequence
        ) { it.second }?.first
    }

    fun findSidebarScrollableNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val rootBounds = root.nodeBounds()

        // 1) 사이드바 신호를 네이티브 텍스트 인덱스로 먼저 고속 확인 (.toList() 전수 순회 완전 차단)
        val closeSidebarNodes = findNodesByText(root, "사이드바 닫기")
        val geminiTitleNodes = findNodesByText(root, "Gemini")
        val hasSidebarSignal = closeSidebarNodes.any { it.matchesTextOrDescription("사이드바 닫기") } ||
            geminiTitleNodes.any { it.matchesTextOrDescription("Gemini") && it.nodeBounds()?.isLikelySidebarHeader(rootBounds) == true }

        if (!hasSidebarSignal) return null

        // 2) 사이드바 신호가 확인되었을 때만 지연 순회로 적합한 스크롤 영역 선택
        return traverseTargetPackage(root)
            .filter { it.isScrollable }
            .mapNotNull { node -> node.nodeBounds()?.let { node to it } }
            .filter { (_, bounds) -> bounds.isLikelySidebarScrollable(rootBounds = rootBounds) }
            .maxByOrNull { (_, bounds) -> bounds.area }
            ?.first
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
        val INPUT_KEYWORDS = listOf("프롬프트", "메시지")
        val SEND_KEYWORDS = listOf("보내기", "전송")
        val SEND_VIEW_IDS = listOf(
            "com.google.android.googlequicksearchbox:id/assistant_robin_input_send_button",
            "com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_send_button"
        )

        val GEMINI_ACCESSIBILITY_PACKAGES = setOf(
            AppDefaults.GEMINI_PACKAGE_NAME,
            AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME
        )
    }
}
