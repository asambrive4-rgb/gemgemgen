package com.example.gemgemgen

import com.example.gemgemgen.automation.android.ChatGptAccessibilityNodeFinder
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptAccessibilityNodeFinderTest {

    @Test
    fun newChatCandidates_containExpectedFallbackKeywords() {
        val candidates = ChatGptAccessibilityNodeFinder.NEW_CHAT_DESCRIPTIONS
        assertTrue(candidates.contains("새 채팅"))
        assertTrue(candidates.contains("새 대화"))
        assertTrue(candidates.contains("New chat"))
    }

    @Test
    fun initialChatCandidates_containEnglishAndKorean() {
        val candidates = ChatGptAccessibilityNodeFinder.INITIAL_CHAT_TEXTS
        assertTrue(candidates.contains("채팅"))
        assertTrue(candidates.contains("Chat"))
    }

    @Test
    fun menuCandidates_containExpectedFallbackKeywords() {
        val candidates = ChatGptAccessibilityNodeFinder.MENU_DESCRIPTIONS
        assertTrue(candidates.contains("메뉴"))
        assertTrue(candidates.contains("사이드바 열기"))
        assertTrue(candidates.contains("Menu"))
    }

    @Test
    fun sendCandidates_containExpectedFallbackKeywords() {
        val candidates = ChatGptAccessibilityNodeFinder.SEND_DESCRIPTIONS
        assertTrue(candidates.contains("메시지 보내기"))
        assertTrue(candidates.contains("전송"))
        assertTrue(candidates.contains("Send message"))
    }
}
