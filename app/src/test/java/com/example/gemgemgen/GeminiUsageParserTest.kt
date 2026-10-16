// 역할: 제미나이 사용량 DOM 본문 텍스트 파싱, 리셋 문구·이메일 추출, 다중 계정 요약 버튼 텍스트 및 70%/90% 경고 단계 계산을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.usage.android.GeminiUsageFetcher
import com.example.gemgemgen.usage.android.MultiProfileGeminiUsageFetcher
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiAccountUsageEntry
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.domain.GeminiUsageWarningLevel
import com.example.gemgemgen.usage.domain.MultiGeminiUsageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiUsageParserTest {

    @Test
    fun parse_koreanRenderedDomText_extractsFiveHourAndWeeklyLimitsAndResetTexts() {
        val bodyText = """
            Google Gemini
            사용량 한도
            5시간 한도
            42% 사용
            2시간 18분 후 초기화
            주간 한도
            78% 사용
            10월 19일 (월) 오전 9:00에 초기화
        """.trimIndent()

        val result = GeminiUsageParser.parse(bodyText = bodyText, updatedAt = 1_760_000_000_000L)

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(42, result.fiveHourPercent)
        assertEquals("2시간 18분 후 초기화", result.fiveHourResetText)
        assertEquals(78, result.weeklyPercent)
        assertEquals("10월 19일 (월) 오전 9:00에 초기화", result.weeklyResetText)
        assertEquals(1_760_000_000_000L, result.updatedAt)
        assertEquals(GeminiUsageWarningLevel.NORMAL, result.fiveHourWarningLevel)
        assertEquals(GeminiUsageWarningLevel.CAUTION, result.weeklyWarningLevel)
        assertEquals(GeminiUsageWarningLevel.CAUTION, result.overallWarningLevel)
    }

    @Test
    fun parse_englishRenderedDomText_extractsPercentagesAndResetPhrases() {
        val bodyText = """
            Gemini usage
            Current limit (5-hour rolling window)
            35% used
            Resets in 3 hr 12 min
            Weekly limit
            92% used
            Resets Mon, Oct 19, 9:00 AM
        """.trimIndent()

        val result = GeminiUsageParser.parse(bodyText = bodyText, updatedAt = 1_760_000_123_000L)

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(35, result.fiveHourPercent)
        assertEquals("Resets in 3 hr 12 min", result.fiveHourResetText)
        assertEquals(92, result.weeklyPercent)
        assertEquals("Resets Mon, Oct 19, 9:00 AM", result.weeklyResetText)
        assertEquals(GeminiUsageWarningLevel.NORMAL, result.fiveHourWarningLevel)
        assertEquals(GeminiUsageWarningLevel.WARNING, result.weeklyWarningLevel)
        assertEquals(GeminiUsageWarningLevel.WARNING, result.overallWarningLevel)
    }

    @Test
    fun parse_compactSlashDateAndRemainingTime_extractsResetStrings() {
        val bodyText = """
            Gemini Pro
            5시간 단기
            74%
            2시간 15분 후
            주간 누적
            91%
            10/19 오전 9시
        """.trimIndent()

        val result = GeminiUsageParser.parse(bodyText = bodyText, updatedAt = 1_760_500_000_000L)

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(74, result.fiveHourPercent)
        assertEquals("2시간 15분 후", result.fiveHourResetText)
        assertEquals(91, result.weeklyPercent)
        assertEquals("10/19 오전 9시", result.weeklyResetText)
        assertEquals(GeminiUsageWarningLevel.CAUTION, result.fiveHourWarningLevel)
        assertEquals(GeminiUsageWarningLevel.WARNING, result.weeklyWarningLevel)
    }

    @Test
    fun parse_whenLessThanTwoPercentagesRendered_returnsNull() {
        val singlePercentDom = """
            Gemini usage
            Loading limits...
            25% used
        """.trimIndent()

        assertFalse(GeminiUsageParser.hasMinimumPercentageTokens(singlePercentDom))
        assertNull(GeminiUsageParser.parse(bodyText = singlePercentDom, updatedAt = 1000L))
    }

    @Test
    fun extractAccountEmail_detectsGoogleEmailInDomText() {
        val domWithEmail = """
            Google 계정: WorkUser.01@gmail.com
            5시간 한도 42%
        """.trimIndent()

        assertEquals("workuser.01@gmail.com", GeminiUsageParser.extractAccountEmail(domWithEmail))
        assertNull(GeminiUsageParser.extractAccountEmail("이메일이 없는 일반 텍스트"))
        assertNull(GeminiUsageParser.extractAccountEmail(null))
    }

    @Test
    fun isAuthRedirectUrl_detectsGoogleAccountsLoginRedirect() {
        assertTrue(
            GeminiUsageParser.isAuthRedirectUrl(
                "https://accounts.google.com/v3/signin/identifier?continue=https://gemini.google.com/usage"
            )
        )
        assertTrue(
            GeminiUsageParser.isAuthRedirectUrl(
                "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fgemini.google.com%2Fusage"
            )
        )
        assertFalse(GeminiUsageParser.isAuthRedirectUrl("https://gemini.google.com/usage"))
        assertFalse(GeminiUsageParser.isAuthRedirectUrl(null))
    }

    @Test
    fun warningLevel_follows70And90PercentThresholds() {
        assertEquals(GeminiUsageWarningLevel.NORMAL, GeminiUsageWarningLevel.fromPercent(0))
        assertEquals(GeminiUsageWarningLevel.NORMAL, GeminiUsageWarningLevel.fromPercent(69))
        assertEquals(GeminiUsageWarningLevel.CAUTION, GeminiUsageWarningLevel.fromPercent(70))
        assertEquals(GeminiUsageWarningLevel.CAUTION, GeminiUsageWarningLevel.fromPercent(89))
        assertEquals(GeminiUsageWarningLevel.WARNING, GeminiUsageWarningLevel.fromPercent(90))
        assertEquals(GeminiUsageWarningLevel.WARNING, GeminiUsageWarningLevel.fromPercent(100))
    }

    @Test
    fun multiGeminiUsageState_formatsRepresentativeSummaryButtonTextAndWarningLevel() {
        val emptyState = MultiGeminiUsageState()
        assertEquals("계정 추가 ▾", emptyState.summaryButtonText)
        assertEquals(GeminiUsageWarningLevel.NORMAL, emptyState.summaryWarningLevel)

        val account1 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_1", alias = "본계정", email = "main@gmail.com", sortOrder = 0),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 42,
                fiveHourResetText = "2시간 15분 후",
                weeklyPercent = 30,
                weeklyResetText = "10/19 오전 9시",
                updatedAt = 1000L
            )
        )
        val account2 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_2", alias = "작업용 1", email = "work1@gmail.com", sortOrder = 1),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 75,
                fiveHourResetText = "1시간 10분 후",
                weeklyPercent = 50,
                weeklyResetText = "10/19 오전 9시",
                updatedAt = 1000L
            )
        )
        val account3 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_3", alias = "작업용 2", email = "work2@gmail.com", sortOrder = 2),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 94,
                fiveHourResetText = "25분 후",
                weeklyPercent = 88,
                weeklyResetText = "10/19 오전 9시",
                updatedAt = 1000L
            )
        )

        val stateWithMainSelected = MultiGeminiUsageState(
            accounts = listOf(account1, account2, account3),
            selectedAccountId = "acc_1"
        )
        assertEquals("본계정 42% ▾", stateWithMainSelected.summaryButtonText)
        assertEquals(GeminiUsageWarningLevel.NORMAL, stateWithMainSelected.summaryWarningLevel)

        val stateWithCautionSelected = stateWithMainSelected.copy(selectedAccountId = "acc_2")
        assertEquals("작업용 1 75% ▾", stateWithCautionSelected.summaryButtonText)
        assertEquals(GeminiUsageWarningLevel.CAUTION, stateWithCautionSelected.summaryWarningLevel)

        val stateWithWarningSelected = stateWithMainSelected.copy(selectedAccountId = "acc_3")
        assertEquals("작업용 2 94% ▾", stateWithWarningSelected.summaryButtonText)
        assertEquals(GeminiUsageWarningLevel.WARNING, stateWithWarningSelected.summaryWarningLevel)
    }

    @Test
    fun sanitizeWebViewUserAgent_removesWebViewMarkerToken() {
        val rawUa =
            "Mozilla/5.0 (Linux; Android 14; SM-S711N Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.106 Mobile Safari/537.36"
        val sanitized = GeminiUsageFetcher.sanitizeWebViewUserAgent(rawUa)
        val multiSanitized = MultiProfileGeminiUsageFetcher.sanitizeWebViewUserAgent(rawUa)

        assertFalse(sanitized.contains("; wv"))
        assertFalse(sanitized.contains("Version/4.0"))
        assertTrue(sanitized.contains("Chrome/130.0.6723.106"))
        assertEquals(sanitized, multiSanitized)
    }

    @Test
    fun parse_whenWeeklySectionHasNoResetLine_doesNotDuplicateFiveHourResetText() {
        val bodyText = """
            Gemini 사용량
            5시간 단기 한도
            0% 사용
            오전 8:00에 초기화
            주간 누적 한도
            19% 사용
        """.trimIndent()

        val result = GeminiUsageParser.parse(bodyText = bodyText, updatedAt = 1_760_000_000_000L)

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(0, result.fiveHourPercent)
        assertEquals("오전 8:00에 초기화", result.fiveHourResetText)
        assertEquals(19, result.weeklyPercent)
        assertEquals("주간 주기 자동 초기화", result.weeklyResetText)
    }

    @Test
    fun isCookieMismatchUrl_detectsGoogleCookieMismatchPages() {
        assertTrue(GeminiUsageParser.isCookieMismatchUrl("https://accounts.google.com/CookieMismatch"))
        assertTrue(GeminiUsageParser.isCookieMismatchUrl("https://accounts.google.com/v3/signin/nocookies"))
        assertFalse(GeminiUsageParser.isCookieMismatchUrl("https://gemini.google.com/usage"))
        assertFalse(GeminiUsageParser.isCookieMismatchUrl(null))
    }

    @Test
    fun formatResetHelpers_stripsNoiseAndComputesRemainingDuration() {
        assertEquals("14:00", GeminiUsageParser.formatShortResetTime("오후 2:00에 초기화"))
        assertEquals("10/20/14:00", GeminiUsageParser.formatShortResetTime("10월 20일 오후 2:00에 초기화"))
        assertEquals("5/3/09:05", GeminiUsageParser.formatShortResetTime("5월 3일 오전 9:05에 초기화"))

        // 기준 시각: 2026년 10월 16일 오전 10:00
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 16, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        assertEquals(
            "14:00 (4시간 후)",
            GeminiUsageParser.formatCompactResetWithRemaining("오후 2:00에 초기화", nowMillis)
        )
        assertEquals(
            "10/20/14:00 (4일 4시간 후)",
            GeminiUsageParser.formatCompactResetWithRemaining("10월 20일 오후 2:00에 초기화", nowMillis)
        )
        assertEquals(
            "2시간 15분 후",
            GeminiUsageParser.formatCompactResetWithRemaining("2시간 15분 후", nowMillis)
        )
    }
}
