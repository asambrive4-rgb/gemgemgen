// 역할: 프롬프트 실행기록 앞/뒤 네비게이터의 상태 전이, 초안 보존 및 순수 과거 기록 점 인디케이터 계산을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptHistoryNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptHistoryNavigatorTest {

    @Test
    fun emptyHistory_cannotNavigate() {
        val navigator = PromptHistoryNavigator(initialHistory = emptyList(), initialDraft = "hello")

        assertFalse(navigator.canNavigateBack)
        assertFalse(navigator.canNavigateForward)
        assertFalse(navigator.isIndicatorVisible)
        assertEquals(0, navigator.dotCount)
        assertNull(navigator.navigateBack("hello"))
        assertNull(navigator.navigateForward())
    }

    @Test
    fun backAndForward_preservesDraftAndUpdatesIndicator() {
        val history = listOf("P1_latest", "P2_middle", "P3_oldest")
        val navigator = PromptHistoryNavigator(initialHistory = history, initialDraft = "MyDraft")

        // 초기 상태 (평소 초안 상태이므로 인디케이터 숨김, 과거 기록 점 개수는 3개)
        assertEquals(3, navigator.dotCount)
        assertEquals(2, navigator.activeDotIndex)
        assertTrue(navigator.canNavigateBack)
        assertFalse(navigator.canNavigateForward)
        assertFalse(navigator.isIndicatorVisible)

        // 1단계 뒤로 (직전 최신 기록 P1_latest 복원, 맨 오른쪽 점)
        val step1 = navigator.navigateBack("MyDraft")
        assertEquals("P1_latest", step1)
        assertEquals(2, navigator.activeDotIndex)
        assertTrue(navigator.isIndicatorVisible)
        assertTrue(navigator.canNavigateBack)
        assertTrue(navigator.canNavigateForward)

        // 2단계 뒤로 (P2_middle 복원, 중간 점)
        val step2 = navigator.navigateBack("P1_latest")
        assertEquals("P2_middle", step2)
        assertEquals(1, navigator.activeDotIndex)
        assertTrue(navigator.canNavigateBack)
        assertTrue(navigator.canNavigateForward)

        // 3단계 뒤로 (P3_oldest 복원, 가장 오래된 기록, 맨 왼쪽 점)
        val step3 = navigator.navigateBack("P2_middle")
        assertEquals("P3_oldest", step3)
        assertEquals(0, navigator.activeDotIndex)
        assertFalse(navigator.canNavigateBack)
        assertTrue(navigator.canNavigateForward)
        assertNull(navigator.navigateBack("P3_oldest"))

        // 앞으로 이동 (P2_middle 복원)
        val fwd1 = navigator.navigateForward()
        assertEquals("P2_middle", fwd1)
        assertEquals(1, navigator.activeDotIndex)

        // 앞으로 이동 (P1_latest 복원, 맨 오른쪽 점)
        val fwd2 = navigator.navigateForward()
        assertEquals("P1_latest", fwd2)
        assertEquals(2, navigator.activeDotIndex)

        // 앞으로 이동 (원래 초안 MyDraft 복원, 인디케이터 숨김)
        val fwd3 = navigator.navigateForward()
        assertEquals("MyDraft", fwd3)
        assertEquals(2, navigator.activeDotIndex)
        assertFalse(navigator.isNavigating)
        assertFalse(navigator.isIndicatorVisible)
        assertFalse(navigator.canNavigateForward)
        assertTrue(navigator.canNavigateBack)
    }

    @Test
    fun userTypingDuringNavigation_resetsToDraftAtLatestPosition() {
        val history = listOf("P1", "P2")
        val navigator = PromptHistoryNavigator(initialHistory = history, initialDraft = "Initial")

        navigator.navigateBack("Initial")
        assertTrue(navigator.isNavigating)
        assertTrue(navigator.isIndicatorVisible)

        navigator.onUserTyping("User typed something")

        assertFalse(navigator.isNavigating)
        assertFalse(navigator.isIndicatorVisible)
        assertFalse(navigator.canNavigateForward)
        assertTrue(navigator.canNavigateBack)
        assertEquals(1, navigator.activeDotIndex)
    }

    @Test
    fun currentTextAlreadyMatchesLatestHistory_navigatesToLatestHistoryWithoutSkipping() {
        val history = listOf("P1_latest", "P2_older")
        val navigator = PromptHistoryNavigator(initialHistory = history, initialDraft = "P1_latest")

        // 현재 텍스트가 최신 히스토리와 같더라도 건너뛰지 않고 직전 최신 기록(맨 오른쪽 점)으로 차례대로 이동
        val restored = navigator.navigateBack("P1_latest")
        assertEquals("P1_latest", restored)
        assertEquals(1, navigator.activeDotIndex)
        assertTrue(navigator.canNavigateBack)
        assertTrue(navigator.canNavigateForward)
    }

    @Test
    fun onAutomationStarted_resetsNavigationAndHidesIndicator() {
        val navigator = PromptHistoryNavigator(
            initialHistory = listOf("P1"),
            initialDraft = "Draft"
        )

        // 사용자가 뒤로가기를 눌러 인디케이터가 켜진 상태
        navigator.navigateBack("Draft")
        assertTrue(navigator.isNavigating)
        assertTrue(navigator.isIndicatorVisible)

        // 자동화 시작 발생 (새 프롬프트 P2 실행)
        navigator.onAutomationStarted(executedPrompt = "P2", updatedHistory = listOf("P2", "P1"))

        // 인디케이터가 즉시 닫히고, 앞뒤 이동 가능 여부와 위치가 최신으로 리셋되어야 함
        assertFalse(navigator.isNavigating)
        assertFalse(navigator.isIndicatorVisible)
        assertFalse(navigator.canNavigateForward)
        assertTrue(navigator.canNavigateBack)
        assertEquals(2, navigator.dotCount) // 과거 기록 P2, P1 = 2개 점
        assertEquals(1, navigator.activeDotIndex) // 맨 오른쪽 최신 위치
    }

    @Test
    fun maxDotsCappedAtFour_whenHistoryHasManyItems() {
        // 5개 이상의 히스토리가 전달되어도 최대 4개 히스토리만 유지되어 총 점은 4개로 한정되어야 함
        val history = listOf("H1", "H2", "H3", "H4", "H5", "H6")
        val navigator = PromptHistoryNavigator(initialHistory = history, initialDraft = "Draft")

        assertEquals(4, navigator.dotCount) // 최대 4개 과거 점
        assertEquals(3, navigator.activeDotIndex)
    }
}
