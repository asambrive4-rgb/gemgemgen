// 역할: 단일 루트 재사용, 안정적 네이티브 ViewId 인덱스 우선 조회, 지연 시퀀스 및 단기 캐시로 Binder IPC를 최소화하며 Gemini 노드를 탐색합니다.
package com.example.gemgemgen.automation.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class GeminiAccessibilityNodeFinder(
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val inputResourceId: String
) {
    private val snapshotCache = AccessibilityNodeSnapshotCache()
    private val inputViewIds = listOf(
        inputResourceId,
        "com.google.android.googlequicksearchbox:id/assistant_robin_input_collapsed_text_half_sheet",
        "com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_text",
        "com.google.android.googlequicksearchbox:id/chat_input_text"
    ).distinct()

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
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("input", root) {
            for (viewId in inputViewIds) {
                findNodeByViewId(root, viewId)?.let { return@getOrFind it }
            }

            for (keyword in INPUT_KEYWORDS) {
                findNodesByText(root, keyword).firstOrNull { it.isInputLike() }?.let { return@getOrFind it }
            }

            AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
                pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
            }.firstOrNull { node -> node.isInputLike() }
        }
    }

    fun findToolbarNewChatNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("toolbar_new_chat", root) {
            val nativeNodes = findNodesByText(root, NEW_CHAT_DESCRIPTION)
            nativeNodes.firstOrNull { node ->
                node.matchesTextOrDescription(NEW_CHAT_DESCRIPTION)
            }?.let { return@getOrFind it }

            AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
                pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
            }.firstOrNull { node ->
                node.matchesTextOrDescription(NEW_CHAT_DESCRIPTION)
            }
        }
    }

    fun findSendNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("send", root) {
            for (viewId in SEND_VIEW_IDS) {
                findNodeByViewId(root, viewId)?.let { return@getOrFind it }
            }

            for (keyword in SEND_KEYWORDS) {
                findNodesByText(root, keyword).firstOrNull { node ->
                    node.matchesTextOrDescription(keyword)
                }?.let { return@getOrFind it }
            }

            val keywordSet = SEND_KEYWORDS.map { it.lowercase() }.toSet()
            AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
                pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
            }.firstOrNull { node ->
                val text = node.text?.toString()?.trim()?.lowercase()
                val desc = node.contentDescription?.toString()?.trim()?.lowercase()
                (text != null && text in keywordSet) || (desc != null && desc in keywordSet)
            }
        }
    }

    fun findNodeByTextOrDescription(value: String): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("text_$value", root) {
            findNodesByText(root, value).firstOrNull { it.matchesTextOrDescription(value) }?.let { return@getOrFind it }

            AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
                pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
            }.firstOrNull { node -> node.matchesTextOrDescription(value) }
        }
    }

    fun hasMoreOptions(): Boolean {
        val root = rootProvider() ?: return false
        return findNodesByText(root, MORE_OPTIONS_DESCRIPTION).any { it.matchesTextOrDescription(MORE_OPTIONS_DESCRIPTION) }
    }

    fun findNewChatWithMoreOptions(): AccessibilityNodeInfo? {
        if (!hasMoreOptions()) return null
        return findNodeByTextOrDescription(NEW_CHAT_DESCRIPTION)
    }

    fun findNewChatNearestToSearch(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val searchNode = findNodeByTextOrDescription("채팅 검색") ?: return null
        val searchBounds = searchNode.nodeBounds() ?: return null

        val nativeCandidates = findNodesByText(root, "새 채팅")
        val candidateBoundsSequence = if (nativeCandidates.isNotEmpty()) {
            nativeCandidates.asSequence()
                .filter { it.matchesTextOrDescription("새 채팅") }
                .mapNotNull { node -> node.nodeBounds()?.let { node to it } }
        } else {
            AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
                pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
            }.filter { it.matchesTextOrDescription("새 채팅") }
                .mapNotNull { node -> node.nodeBounds()?.let { node to it } }
        }

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
        return AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
            pkg?.toString() in GEMINI_ACCESSIBILITY_PACKAGES
        }.filter { it.isScrollable }
            .mapNotNull { node -> node.nodeBounds()?.let { node to it } }
            .filter { (_, bounds) -> bounds.isLikelySidebarScrollable(rootBounds = rootBounds) }
            .maxByOrNull { (_, bounds) -> bounds.area }
            ?.first
    }

    private fun findNodeByViewId(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        return try {
            root.findAccessibilityNodeInfosByViewId(viewId)
                ?.firstOrNull { node -> node.isGeminiPackage() }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun findNodesByText(root: AccessibilityNodeInfo, value: String): List<AccessibilityNodeInfo> {
        return try {
            root.findAccessibilityNodeInfosByText(value)
                ?.filter { it.isGeminiPackage() }
                .orEmpty()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun AccessibilityNodeInfo.isInputLike(): Boolean {
        return isEditable || className?.contains("EditText", ignoreCase = true) == true
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
        val INPUT_KEYWORDS = listOf("프롬프트", "메시지", "Message", "Ask Gemini", "질문하기", "여기에 메시지 입력")
        val SEND_KEYWORDS = listOf("보내기", "전송", "Send", "메시지 보내기", "프롬프트 보내기")
        val SEND_VIEW_IDS = listOf(
            "com.google.android.googlequicksearchbox:id/assistant_robin_input_send_button",
            "com.google.android.googlequicksearchbox:id/gemini_chat_input_send_button",
            "com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_send_button",
            "com.google.android.googlequicksearchbox:id/chat_input_send_button"
        )

        val GEMINI_ACCESSIBILITY_PACKAGES = setOf(
            AppDefaults.GEMINI_PACKAGE_NAME,
            AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME
        )
    }
}
