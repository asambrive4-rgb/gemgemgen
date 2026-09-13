// 역할: Gemini 앱 화면의 접근성 노드 탐색 및 전환 대기 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.GeminiAccessibilityNodeFinder
import com.example.gemgemgen.core.AppDefaults
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAccessibilityNodeFinderTest {

    @Test
    fun directNewChatCandidates_containExpectedFallbackKeywords() {
        val candidates = GeminiAccessibilityNodeFinder.DIRECT_NEW_CHAT_DESCRIPTIONS
        assertTrue(candidates.contains("새 채팅"))
        assertTrue(candidates.contains("새 대화"))
        assertTrue(candidates.contains("New chat"))
    }

    @Test
    fun activeChatOptionsCandidates_containExpectedFallbackKeywords() {
        val candidates = GeminiAccessibilityNodeFinder.ACTIVE_CHAT_OPTIONS_DESCRIPTIONS
        assertTrue(candidates.contains("옵션 더보기"))
        assertTrue(candidates.contains("More options"))
    }

    @Test
    fun tempChatExclusionKeywords_containExpectedKeywords() {
        val exclusions = GeminiAccessibilityNodeFinder.TEMP_CHAT_EXCLUSION_KEYWORDS
        assertTrue(exclusions.contains("임시"))
        assertTrue(exclusions.contains("temporary"))
    }

    @Test
    fun accessibilityPackages_containGeminiAndGoogleSearchBox() {
        val packages = GeminiAccessibilityNodeFinder.GEMINI_ACCESSIBILITY_PACKAGES
        assertTrue(packages.contains(AppDefaults.GEMINI_PACKAGE_NAME))
        assertTrue(packages.contains(AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME))
    }

    @Test
    fun stopGeneratingCandidates_containExpectedKeywords() {
        val candidates = GeminiAccessibilityNodeFinder.STOP_GENERATING_DESCRIPTIONS
        assertTrue(candidates.contains("중지"))
        assertTrue(candidates.contains("응답 중지"))
        assertTrue(candidates.contains("생성 중지"))
        assertTrue(candidates.contains("Stop"))
    }

    @Test
    fun responseCompletedIdleCandidates_containExpectedKeywords() {
        val candidates = GeminiAccessibilityNodeFinder.RESPONSE_COMPLETED_IDLE_DESCRIPTIONS
        assertTrue(candidates.contains("마이크"))
        assertTrue(candidates.contains("Open Gemini Live"))
        assertTrue(candidates.contains("보내기"))
    }
}