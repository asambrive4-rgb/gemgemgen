// 역할: Gemini 구글 계정 전환 접근성 노드 라벨 및 계정 식별자 매칭 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.GeminiAccountMatcher
import com.example.gemgemgen.automation.android.GeminiAccountNodeCandidate
import com.example.gemgemgen.automation.android.GeminiAccountSwitchNodeLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAccountSwitchAutomationTest {

    @Test
    fun defaultNodeLabels_containsOneGoogleBentoAndRobinToolbarIds() {
        val labels = GeminiAccountSwitchNodeLabels()
        assertTrue(labels.toolbarViewIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_chat_compose_toolbar"))
        assertTrue(labels.bentoMenuContainerIds.contains("com.google.android.googlequicksearchbox:id/og_bento_menu_content_container"))
        assertTrue(labels.accountHeaderContainerIds.contains("com.google.android.googlequicksearchbox:id/og_bento_account_management_header_container"))
        assertTrue(labels.availableAccountRowIds.contains("com.google.android.googlequicksearchbox:id/og_bento_available_account_root"))
        assertTrue(labels.accountPrimaryInfoIds.contains("com.google.android.googlequicksearchbox:id/og_primary_account_information"))
        assertTrue(labels.accountSecondaryInfoIds.contains("com.google.android.googlequicksearchbox:id/og_secondary_account_information"))
    }

    @Test
    fun matcher_withTargetEmail_matchesOnlyExactEmailOrLocalPart() {
        val candidate1 = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "joajoajoa31@gmail.com"
        )
        val candidate2 = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "jjoajjoajjoa31@gmail.com"
        )
        val candidate3 = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "jjjjoajjjjoajjjjoa31@gmail.com"
        )

        assertTrue(
            GeminiAccountMatcher.isExactMatch(
                candidate = candidate1,
                targetEmail = "joajoajoa31@gmail.com",
                targetAlias = "계정 1"
            )
        )
        assertFalse(
            GeminiAccountMatcher.isExactMatch(
                candidate = candidate2,
                targetEmail = "joajoajoa31@gmail.com",
                targetAlias = "계정 1"
            )
        )
        assertFalse(
            GeminiAccountMatcher.isExactMatch(
                candidate = candidate3,
                targetEmail = "joajoajoa31@gmail.com",
                targetAlias = "계정 1"
            )
        )
    }

    @Test
    fun matcher_withLocalPartInTargetEmail_matchesExactLocalPartOnly() {
        val candidate1 = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "joajoajoa31@gmail.com"
        )
        val candidate2 = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "jjoajjoajjoa31@gmail.com"
        )

        assertTrue(
            GeminiAccountMatcher.isExactMatch(
                candidate = candidate1,
                targetEmail = "joajoajoa31",
                targetAlias = "본계정"
            )
        )
        assertEquals(
            0,
            GeminiAccountMatcher.scoreCandidate(
                candidate = candidate2,
                targetEmail = "joajoajoa31",
                targetAlias = "본계정"
            )
        )
    }

    @Test
    fun matcher_whenEmailBlank_usesAliasAndPrefersShortestMatchingLocalPart() {
        val candidateShort = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "joajoajoa31@gmail.com"
        )
        val candidateMedium = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "jjoajjoajjoa31@gmail.com"
        )
        val candidateLong = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "jjjjoajjjjoajjjjoa31@gmail.com"
        )

        // 별칭에 이메일 앞부분을 정확히 적은 경우
        assertTrue(
            GeminiAccountMatcher.isExactMatch(
                candidate = candidateMedium,
                targetEmail = "",
                targetAlias = "jjoajjoajjoa31"
            )
        )

        // 별칭에 공통 부분 문자열("joa31")만 적은 경우 가장 짧은 로컬 파트가 가장 높은 점수를 받음
        val scoreShort = GeminiAccountMatcher.scoreCandidate(candidateShort, "", "joa31")
        val scoreMedium = GeminiAccountMatcher.scoreCandidate(candidateMedium, "", "joa31")
        val scoreLong = GeminiAccountMatcher.scoreCandidate(candidateLong, "", "joa31")

        assertTrue(scoreShort > scoreMedium)
        assertTrue(scoreMedium > scoreLong)
        assertTrue(scoreLong > 0)
    }

    @Test
    fun extractEmail_fallsBackToContentDescriptionWhenSecondaryBlank() {
        val headerCandidate = GeminiAccountNodeCandidate(
            primaryText = "조조",
            secondaryText = "",
            contentDescription = "조조 jjoajjoajjoa31@gmail.com 계정으로 로그인함"
        )

        assertEquals(
            "jjoajjoajjoa31@gmail.com",
            GeminiAccountMatcher.extractEmail(headerCandidate)
        )
        assertTrue(
            GeminiAccountMatcher.isExactMatch(
                candidate = headerCandidate,
                targetEmail = "jjoajjoajjoa31@gmail.com",
                targetAlias = "부계정"
            )
        )
    }
}
