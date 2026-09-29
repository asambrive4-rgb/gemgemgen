// 역할: ChatGPT 앱 화면의 접근성 노드 탐색 로직과 한국어 키워드 상수를 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.ChatGptAccessibilityNodeFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatGptAccessibilityNodeFinderTest {

    @Test
    fun inputKeywords_containExpectedKeywords() {
        assertEquals(listOf("메시지", "프롬프트"), ChatGptAccessibilityNodeFinder.INPUT_KEYWORDS)
    }

    @Test
    fun newChatCandidates_containExpectedFallbackKeywords() {
        assertEquals(listOf("새 채팅", "새 대화"), ChatGptAccessibilityNodeFinder.NEW_CHAT_DESCRIPTIONS)
    }

    @Test
    fun initialChatCandidates_containExpectedKeywords() {
        assertEquals(listOf("채팅", "대화"), ChatGptAccessibilityNodeFinder.INITIAL_CHAT_TEXTS)
    }

    @Test
    fun menuCandidates_containExpectedFallbackKeywords() {
        assertEquals(listOf("사이드바 열기", "메뉴"), ChatGptAccessibilityNodeFinder.MENU_DESCRIPTIONS)
    }

    @Test
    fun sendCandidates_containExpectedFallbackKeywords() {
        assertEquals(listOf("보내기", "전송"), ChatGptAccessibilityNodeFinder.SEND_DESCRIPTIONS)
    }

    @Test
    fun settingsCandidates_containExpectedKeywords() {
        assertEquals(listOf("설정", "계정 설정"), ChatGptAccessibilityNodeFinder.SETTINGS_DESCRIPTIONS)
    }

    @Test
    fun tooManyRequestsConstants_areDefinedAsExpected() {
        assertEquals("Too many requests", ChatGptAccessibilityNodeFinder.TOO_MANY_REQUESTS_MESSAGE)
        assertEquals("닫기", ChatGptAccessibilityNodeFinder.TOO_MANY_REQUESTS_CLOSE_DESCRIPTION)
    }

    @Test
    fun findTooManyRequestsCloseNode_returnsNullWhenRootIsNull() {
        val finder = ChatGptAccessibilityNodeFinder { null }
        assertNull(finder.findTooManyRequestsCloseNode())
    }
}
