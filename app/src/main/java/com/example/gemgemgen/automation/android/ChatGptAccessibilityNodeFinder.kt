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
        return nodes().firstOrNull { node ->
            node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                node.isEditable
        }
    }

    fun findMenuNode(): AccessibilityNodeInfo? {
        return findFirstNodeByDescriptions(MENU_DESCRIPTIONS)
    }

    fun isMenuOpen(): Boolean {
        return findFirstNodeByDescriptions(SEARCH_DESCRIPTIONS) != null &&
            findFirstNodeByDescriptions(SETTINGS_DESCRIPTIONS) != null
    }

    fun findInitialChatNode(): AccessibilityNodeInfo? {
        // 1) 최신 버전(1.2026.244+): 사이드바 상단 '새 채팅' 버튼 우선 탐색
        findFirstNodeByDescriptions(NEW_CHAT_DESCRIPTIONS)?.let { return it }

        // 2) 이전 버전 및 다국어: 사이드바 하단 '채팅'/'Chat' 텍스트 노드 탐색
        val nodeList = nodes()
        for (candidate in INITIAL_CHAT_TEXTS) {
            val textMatch = nodeList.firstOrNull { node ->
                node.text?.toString()?.trim()?.equals(candidate, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.trim()?.equals(candidate, ignoreCase = true) == true
            }
            if (textMatch != null) return textMatch
        }

        return null
    }

    fun findNewChatNode(): AccessibilityNodeInfo? {
        return findFirstNodeByDescriptions(NEW_CHAT_DESCRIPTIONS)
    }

    fun findSendNode(): AccessibilityNodeInfo? {
        return findFirstNodeByDescriptions(SEND_DESCRIPTIONS)
    }

    fun findTooManyRequestsCloseNode(): AccessibilityNodeInfo? {
        val hasTooManyRequestsMessage = nodes().any { node ->
            node.text?.toString()?.contains(TOO_MANY_REQUESTS_MESSAGE, ignoreCase = true) == true
        }
        if (!hasTooManyRequestsMessage) return null

        return findNodeByDescription(TOO_MANY_REQUESTS_CLOSE_DESCRIPTION)
    }

    private fun findNodeByDescription(value: String): AccessibilityNodeInfo? {
        val trimmed = value.trim()
        return nodes().firstOrNull { node ->
            node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
        }
    }

    private fun findFirstNodeByDescriptions(candidates: List<String>): AccessibilityNodeInfo? {
        val nodeList = nodes()
        for (candidate in candidates) {
            val trimmed = candidate.trim()
            val match = nodeList.firstOrNull { node ->
                node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
            }
            if (match != null) return match
        }
        return null
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        return snapshotCache.getOrLoad(rootProvider()) { root ->
            val nodes = mutableListOf<AccessibilityNodeInfo>()

            fun visit(node: AccessibilityNodeInfo) {
                if (node.packageName?.toString() == AppDefaults.CHATGPT_PACKAGE_NAME) {
                    nodes += node
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(::visit)
                }
            }

            visit(root)
            nodes
        }
    }

    internal companion object {
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
