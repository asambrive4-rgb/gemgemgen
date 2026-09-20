// 역할: 단일 루트 재사용, 네이티브 인덱스 기반 조기 종료 및 단기 스냅샷 캐시로 Binder IPC를 최소화하며 ChatGPT 노드를 탐색합니다.
package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class ChatGptAccessibilityNodeFinder(
    private val rootProvider: () -> AccessibilityNodeInfo?
) {
    private val snapshotCache = AccessibilityNodeSnapshotCache()

    fun invalidateCache() {
        snapshotCache.clear()
    }

    fun findInputNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("input", root) {
            for (keyword in INPUT_KEYWORDS) {
                findNodesByText(root, keyword).firstOrNull { node ->
                    node.className?.toString()?.contains("EditText", ignoreCase = true) == true || node.isEditable
                }?.let { return@getOrFind it }
            }

            nodesSequence(root).firstOrNull { node ->
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                    node.isEditable
            }
        }
    }

    fun findMenuNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("menu", root) {
            findFirstNodeByDescriptions(root, MENU_DESCRIPTIONS)
        }
    }

    fun isMenuOpen(): Boolean {
        val root = rootProvider() ?: return false
        return findFirstNodeByDescriptions(root, SEARCH_DESCRIPTIONS) != null &&
            findFirstNodeByDescriptions(root, SETTINGS_DESCRIPTIONS) != null
    }

    fun findInitialChatNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        // 1) 최신 버전(1.2026.244+): 사이드바 상단 '새 채팅' 버튼 우선 탐색
        findFirstNodeByDescriptions(root, NEW_CHAT_DESCRIPTIONS)?.let { return it }

        // 2) 이전 버전 및 다국어: 사이드바 하단 '채팅'/'Chat' 텍스트 노드 탐색
        for (candidate in INITIAL_CHAT_TEXTS) {
            val trimmed = candidate.trim()
            findNodesByText(root, trimmed).firstOrNull { node ->
                node.text?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
            }?.let { return it }
        }

        val initialSet = INITIAL_CHAT_TEXTS.map { it.trim().lowercase() }.toSet()
        return nodesSequence(root).firstOrNull { node ->
            val text = node.text?.toString()?.trim()?.lowercase()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase()
            (text != null && text in initialSet) || (desc != null && desc in initialSet)
        }
    }

    fun findNewChatNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("new_chat", root) {
            findFirstNodeByDescriptions(root, NEW_CHAT_DESCRIPTIONS)
        }
    }

    fun findSendNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("send", root) {
            findFirstNodeByDescriptions(root, SEND_DESCRIPTIONS)
        }
    }

    fun findTooManyRequestsCloseNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        if (findNodesByText(root, TOO_MANY_REQUESTS_MESSAGE).isEmpty()) return null

        return findNodeByDescription(root, TOO_MANY_REQUESTS_CLOSE_DESCRIPTION)
    }

    private fun findNodeByDescription(root: AccessibilityNodeInfo, value: String): AccessibilityNodeInfo? {
        val trimmed = value.trim()
        findNodesByText(root, trimmed).firstOrNull { node ->
            node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
        }?.let { return it }

        return nodesSequence(root).firstOrNull { node ->
            node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
        }
    }

    private fun findFirstNodeByDescriptions(root: AccessibilityNodeInfo, candidates: List<String>): AccessibilityNodeInfo? {
        for (candidate in candidates) {
            val trimmed = candidate.trim()
            val nativeMatch = findNodesByText(root, trimmed).firstOrNull { node ->
                node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true ||
                    node.text?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
            }
            if (nativeMatch != null) return nativeMatch
        }

        val candidateSet = candidates.map { it.trim().lowercase() }.toSet()
        return nodesSequence(root).firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim()?.lowercase()
            val text = node.text?.toString()?.trim()?.lowercase()
            (desc != null && desc in candidateSet) || (text != null && text in candidateSet)
        }
    }

    private fun findNodesByText(root: AccessibilityNodeInfo, value: String): List<AccessibilityNodeInfo> {
        return try {
            root.findAccessibilityNodeInfosByText(value)
                ?.filter { it.packageName?.toString() == AppDefaults.CHATGPT_PACKAGE_NAME }
                .orEmpty()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun nodesSequence(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> {
        return AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
            pkg?.toString() == AppDefaults.CHATGPT_PACKAGE_NAME
        }
    }

    internal companion object {
        val INPUT_KEYWORDS = listOf("메시지", "Message", "Ask", "질문", "프롬프트")
        val NEW_CHAT_DESCRIPTIONS = listOf("새 채팅", "새 대화", "새로운 채팅", "New chat")
        val INITIAL_CHAT_TEXTS = listOf("채팅", "Chat", "대화")
        val MENU_DESCRIPTIONS = listOf("메뉴", "사이드바 열기", "탐색 창 열기", "Menu", "Open navigation drawer")
        val SEND_DESCRIPTIONS = listOf("메시지 보내기", "보내기", "전송", "Send message", "Send")
        val SEARCH_DESCRIPTIONS = listOf("검색", "Search")
        val SETTINGS_DESCRIPTIONS = listOf("계정 설정", "설정", "Settings", "Account settings")
        const val TOO_MANY_REQUESTS_MESSAGE = "Too many requests"
        const val TOO_MANY_REQUESTS_CLOSE_DESCRIPTION = "닫기"
    }
}
