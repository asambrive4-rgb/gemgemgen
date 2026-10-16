// 역할: 제미나이 사용량 DOM 본문 파싱, 리셋 시간 계산·만료 보정, 계정 정렬(최적 사용 순서 및 소진 분리) 및 요약 상태를 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.usage.android.GeminiUsageFetcher
import com.example.gemgemgen.usage.android.MultiProfileGeminiUsageFetcher
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiAccountUsageEntry
import com.example.gemgemgen.usage.domain.GeminiQuotaExhaustionReason
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

    @Test
    fun computeRemainingResetMillis_handlesRelativeAndAbsoluteTimes() {
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 16, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        // 1. 기본 문구는 null
        assertNull(GeminiUsageParser.computeRemainingResetMillis("5시간 주기 자동 초기화", nowMillis))
        assertNull(GeminiUsageParser.computeRemainingResetMillis("주간 주기 자동 초기화", nowMillis))

        // 2. 상대 시간 (updatedAt 없음)
        assertEquals(
            (2 * 3600_000L + 18 * 60_000L),
            GeminiUsageParser.computeRemainingResetMillis("2시간 18분 후 초기화", nowMillis)
        )
        assertEquals(
            (3 * 3600_000L + 12 * 60_000L),
            GeminiUsageParser.computeRemainingResetMillis("Resets in 3 hr 12 min", nowMillis)
        )
        assertEquals(
            45 * 60_000L,
            GeminiUsageParser.computeRemainingResetMillis("45 min", nowMillis)
        )

        // 3. 상대 시간 (updatedAt 있음)
        val updatedAt = nowMillis - 18 * 60_000L // 18분 전에 2시간 18분 후라고 조회됨
        assertEquals(
            2 * 3600_000L, // 2시간 남음
            GeminiUsageParser.computeRemainingResetMillis("2시간 18분 후 초기화", nowMillis, updatedAt = updatedAt)
        )

        // 4. 절대 시각 (오후 2:00 = 14:00, 현재 10:00이므로 4시간 후)
        assertEquals(
            4 * 3600_000L,
            GeminiUsageParser.computeRemainingResetMillis("오후 2:00에 초기화", nowMillis)
        )
    }

    @Test
    fun accountsSorting_userScenario_sortsUsableAndExhaustedCorrectly() {
        // 기준 시각: 2026-10-16 12:00
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 16, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        // 계정 1: 4시간 뒤 초기화, 5시간 40%, 주간 20%, sortOrder=0
        val account1 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_1", alias = "계정 1", email = "acc1@gmail.com", sortOrder = 0),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 40,
                fiveHourResetText = "4시간 후 초기화",
                weeklyPercent = 20,
                weeklyResetText = "3일 후 초기화",
                updatedAt = nowMillis
            )
        )

        // 계정 2: 2시간 뒤 초기화, 5시간 40%, 주간 20%, sortOrder=1
        val account2 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_2", alias = "계정 2", email = "acc2@gmail.com", sortOrder = 1),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 40,
                fiveHourResetText = "2시간 후 초기화",
                weeklyPercent = 20,
                weeklyResetText = "3일 후 초기화",
                updatedAt = nowMillis
            )
        )

        // 계정 3: 1시간 뒤 5시간 초기화지만, 주간 100% 소진 (3일 뒤 초기화), sortOrder=2
        val account3 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_3", alias = "계정 3", email = "acc3@gmail.com", sortOrder = 2),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 30,
                fiveHourResetText = "1시간 후 초기화",
                weeklyPercent = 100,
                weeklyResetText = "3일 후 초기화",
                updatedAt = nowMillis
            )
        )

        // 계정 4: 30분 뒤 초기화, 5시간 100% 소진, 주간 30%, sortOrder=3
        val account4 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_4", alias = "계정 4", email = "acc4@gmail.com", sortOrder = 3),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 100,
                fiveHourResetText = "30분 후 초기화",
                weeklyPercent = 30,
                weeklyResetText = "3일 후 초기화",
                updatedAt = nowMillis
            )
        )

        val state = MultiGeminiUsageState(
            accounts = listOf(account1, account2, account3, account4)
        )

        // 1. 소진 사유 판별 검증
        assertNull(account1.quotaExhaustionReason(nowMillis))
        assertNull(account2.quotaExhaustionReason(nowMillis))
        assertEquals(GeminiQuotaExhaustionReason.WEEKLY_EXHAUSTED, account3.quotaExhaustionReason(nowMillis))
        assertEquals(GeminiQuotaExhaustionReason.FIVE_HOUR_EXHAUSTED, account4.quotaExhaustionReason(nowMillis))

        // 2. 섹션 분리 검증
        val available = state.availableAccountsSorted(nowMillis)
        val exhausted = state.exhaustedAccountsSorted(nowMillis)
        assertEquals(listOf("acc_2", "acc_1"), available.map { it.profile.id })
        assertEquals(listOf("acc_4", "acc_3"), exhausted.map { it.profile.id })

        // 3. 전체 정렬 순서 검증: [계정 2 -> 계정 1 -> 계정 4 -> 계정 3]
        val sorted = state.sortedAccountsForUsage(nowMillis)
        assertEquals(listOf("acc_2", "acc_1", "acc_4", "acc_3"), sorted.map { it.profile.id })
    }

    @Test
    fun accountsSorting_unstartedTimerPlacedAtTopAndDifferentiatedFromZeroPercentRunningTimer() {
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 16, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        // 계정 0: 미시작 계정 (0%, "5시간 주기 자동 초기화", 주간 10%)
        val account0 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_0", alias = "미시작 계정", email = "acc0@gmail.com", sortOrder = 0),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 0,
                fiveHourResetText = "5시간 주기 자동 초기화",
                weeklyPercent = 10,
                weeklyResetText = "주간 주기 자동 초기화",
                updatedAt = nowMillis
            )
        )

        // 계정 1: 과거 시각 캐시 만료로 미시작 처리된 계정 (과거 10:00 초기화 캐시)
        val account1 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_1", alias = "만료 계정", email = "acc1@gmail.com", sortOrder = 1),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 80,
                fiveHourResetText = "오전 10:00에 초기화",
                weeklyPercent = 20,
                weeklyResetText = "주간 주기 자동 초기화",
                updatedAt = nowMillis - 3 * 3600_000L // 3시간 전(09:00) 갱신
            )
        )

        // 계정 2: 0%이지만 미래 3시간 후 초기화 시각이 명시되어 타이머가 이미 시작된 계정
        val account2 = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_2", alias = "타이머 진행중 0%", email = "acc2@gmail.com", sortOrder = 2),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 0,
                fiveHourResetText = "3시간 후 초기화",
                weeklyPercent = 5,
                weeklyResetText = "주간 주기 자동 초기화",
                updatedAt = nowMillis
            )
        )

        assertTrue(account0.isFiveHourTimerUnstarted(nowMillis))
        assertTrue(account1.isFiveHourTimerUnstarted(nowMillis))
        assertFalse(account2.isFiveHourTimerUnstarted(nowMillis))
        assertEquals(0, account1.effectiveFiveHourPercent(nowMillis)) // 캐시 만료로 0%

        val state = MultiGeminiUsageState(accounts = listOf(account2, account1, account0))
        val sorted = state.availableAccountsSorted(nowMillis)

        // 미시작 계정(acc_0, acc_1)이 진행 중 계정(acc_2)보다 앞에 오고,
        // 미시작 계정끼리는 주간 사용률 오름차순(acc_0: 10% vs acc_1: 20%) -> acc_0이 먼저!
        assertEquals(listOf("acc_0", "acc_1", "acc_2"), sorted.map { it.profile.id })
    }

    @Test
    fun accountsSorting_tieBreaking_resolvesByFiveHourPercentThenSortOrder() {
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 16, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        // 둘 다 2시간 후 초기화지만, acc_a는 30%, acc_b는 60%
        val accountA = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_a", alias = "잔여 많은 계정", email = "a@gmail.com", sortOrder = 1),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 30,
                fiveHourResetText = "2시간 후 초기화",
                weeklyPercent = 20,
                weeklyResetText = "주간 주기 자동 초기화",
                updatedAt = nowMillis
            )
        )
        val accountB = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(id = "acc_b", alias = "잔여 적은 계정", email = "b@gmail.com", sortOrder = 0),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 60,
                fiveHourResetText = "2시간 후 초기화",
                weeklyPercent = 20,
                weeklyResetText = "주간 주기 자동 초기화",
                updatedAt = nowMillis
            )
        )

        val state = MultiGeminiUsageState(accounts = listOf(accountB, accountA))
        val sorted = state.availableAccountsSorted(nowMillis)
        // fiveHourPercent가 30%로 더 낮은(잔여량이 많은) accountA가 먼저 와야 함
        assertEquals(listOf("acc_a", "acc_b"), sorted.map { it.profile.id })
    }

    @Test
    fun computeRemainingResetMillis_fiveHourExpiredCacheDoesNotRollOverToNextDay() {
        // 오전 10:30 기준
        val cal1030 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 16, 10, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = cal1030.timeInMillis

        // 오전 8:00에 조회된 "오전 10:00에 초기화" (45% 사용)
        val cal0800 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 16, 8, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val updatedAt = cal0800.timeInMillis

        val remainingWithUpdated = GeminiUsageParser.computeRemainingResetMillis(
            rawText = "오전 10:00에 초기화",
            nowMillis = nowMillis,
            updatedAt = updatedAt,
            isFiveHourWindow = true
        )
        assertNotNull(remainingWithUpdated)
        requireNotNull(remainingWithUpdated)
        assertTrue("8시에 본 10시 초기화는 10:30에 이미 지난 시각이므로 <= 0L이어야 함", remainingWithUpdated <= 0L)

        // updatedAt이 0L이어도 isFiveHourWindow == true이면 내일 10시(23.5시간 후)로 밀리지 않고 <= 0L로 보정되어야 함
        val remainingWithoutUpdated = GeminiUsageParser.computeRemainingResetMillis(
            rawText = "오전 10:00에 초기화",
            nowMillis = nowMillis,
            updatedAt = 0L,
            isFiveHourWindow = true
        )
        assertNotNull(remainingWithoutUpdated)
        requireNotNull(remainingWithoutUpdated)
        assertTrue("updatedAt=0L이어도 5시간 윈도우에서 6시간 초과 미래 시각은 당일 만료(<= 0L)로 보정되어야 함", remainingWithoutUpdated <= 0L)

        // formatCompactResetWithRemaining 검증: "초기화됨 (0%)" 반환 확인
        assertEquals(
            "초기화됨 (0%)",
            GeminiUsageParser.formatCompactResetWithRemaining(
                rawText = "오전 10:00에 초기화",
                nowMillis = nowMillis,
                updatedAt = updatedAt,
                isFiveHourWindow = true
            )
        )
    }
}
