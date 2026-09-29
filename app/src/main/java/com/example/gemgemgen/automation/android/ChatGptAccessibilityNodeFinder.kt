// 역할: 공통 NodeFinder 기반 위에서 ChatGPT 전용 메뉴·채팅·전송 및 요청 제한 알림 노드를 탐색합니다.
package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class ChatGptAccessibilityNodeFinder(
    rootProvider: () -> AccessibilityNodeInfo?
) : BaseAccessibilityNodeFinder(rootProvider, setOf(AppDefaults.CHATGPT_PACKAGE_NAME)) {

    fun findInputNode(): AccessibilityNodeInfo? =
        findInputNodeBy(keywords = INPUT_KEYWORDS)

    fun findMenuNode(): AccessibilityNodeInfo? =
        cachedNode("menu") { root ->
            findFirstNodeByExactCandidates(root, MENU_DESCRIPTIONS)
        }

    fun isMenuOpen(): Boolean {
        val root = rootProvider() ?: return false
        return findFirstNodeByExactCandidates(root, SEARCH_DESCRIPTIONS) != null &&
            findFirstNodeByExactCandidates(root, SETTINGS_DESCRIPTIONS) != null
    }

    fun findInitialChatNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        // 1) 최신 버전(1.2026.244+): 사이드바 상단 '새 채팅' 버튼 우선 탐색
        findFirstNodeByExactCandidates(root, NEW_CHAT_DESCRIPTIONS)?.let { return it }

        // 2) 이전 버전 및 다국어: 사이드바 하단 '채팅'/'Chat' 텍스트 노드 탐색
        return findFirstNodeByExactCandidates(root, INITIAL_CHAT_TEXTS)
    }

    fun findNewChatNode(): AccessibilityNodeInfo? =
        cachedNode("new_chat") { root ->
            findFirstNodeByExactCandidates(root, NEW_CHAT_DESCRIPTIONS)
        }

    fun findSendNode(): AccessibilityNodeInfo? =
        cachedNode("send") { root ->
            findFirstNodeByExactCandidates(root, SEND_DESCRIPTIONS)
        }

    fun findTooManyRequestsCloseNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        if (findNodesByText(root, TOO_MANY_REQUESTS_MESSAGE).isEmpty()) return null

        return findFirstNodeByDescription(root, TOO_MANY_REQUESTS_CLOSE_DESCRIPTION)
    }

    internal companion object {
        val INPUT_KEYWORDS = listOf("메시지", "프롬프트")
        val NEW_CHAT_DESCRIPTIONS = listOf("새 채팅", "새 대화")
        val INITIAL_CHAT_TEXTS = listOf("채팅", "대화")
        val MENU_DESCRIPTIONS = listOf("사이드바 열기", "메뉴")
        val SEND_DESCRIPTIONS = listOf("보내기", "전송")
        val SEARCH_DESCRIPTIONS = listOf("검색", "Search")
        val SETTINGS_DESCRIPTIONS = listOf("설정", "계정 설정")
        const val TOO_MANY_REQUESTS_MESSAGE = "Too many requests"
        const val TOO_MANY_REQUESTS_CLOSE_DESCRIPTION = "닫기"
    }
}
