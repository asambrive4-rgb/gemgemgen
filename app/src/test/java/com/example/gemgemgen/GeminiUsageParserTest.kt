// 역할: 제미나이 사용량 DOM 본문 텍스트 파싱, 리셋 문구 추출, 로그인 리다이렉트 감지 및 70%/90% 경고 색상 단계 계산을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.usage.android.GeminiUsageFetcher
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageWarningLevel
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
    fun sanitizeWebViewUserAgent_removesWebViewMarkerToken() {
        val rawUa =
            "Mozilla/5.0 (Linux; Android 14; SM-S711N Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.106 Mobile Safari/537.36"
        val sanitized = GeminiUsageFetcher.sanitizeWebViewUserAgent(rawUa)

        assertFalse(sanitized.contains("; wv"))
        assertFalse(sanitized.contains("Version/4.0"))
        assertTrue(sanitized.contains("Chrome/130.0.6723.106"))
    }
}
