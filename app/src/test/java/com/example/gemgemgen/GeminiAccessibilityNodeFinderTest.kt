// 역할: Gemini 앱 화면의 접근성 패키지 식별 및 새 채팅/옵션 더보기 노드 탐색 설정을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.GeminiAccessibilityNodeFinder
import com.example.gemgemgen.core.AppDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAccessibilityNodeFinderTest {

    @Test
    fun accessibilityPackages_containGeminiAndGoogleSearchBox() {
        val packages = GeminiAccessibilityNodeFinder.GEMINI_ACCESSIBILITY_PACKAGES
        assertTrue(packages.contains(AppDefaults.GEMINI_PACKAGE_NAME))
        assertTrue(packages.contains(AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME))
    }

    @Test
    fun inChatNewChatCandidates_containExpectedKeywords() {
        assertEquals("새 채팅", GeminiAccessibilityNodeFinder.NEW_CHAT_DESCRIPTION)
        assertEquals("옵션 더보기", GeminiAccessibilityNodeFinder.MORE_OPTIONS_DESCRIPTION)
    }
}