// 역할: 다중 제미나이 계정의 개별 사용량 항목과 전체 목록 상태, 5시간·주간 리셋 만료 자동 보정, 메인/서브 그룹별 분류·정렬, 소진 판별·사유, 대표 계정 요약 규칙을 정의합니다.
package com.example.gemgemgen.usage.domain

/**
 * 제미나이 계정의 할당량 소진 사유.
 */
enum class GeminiQuotaExhaustionReason(val badgeLabel: String) {
    FIVE_HOUR_EXHAUSTED("5시간 소진"),
    WEEKLY_EXHAUSTED("주간 소진"),
    MANUAL_EXHAUSTED("수동 제외")
}

/**
 * 수직 목록 모달의 각 행에 대응하는 단일 계정의 프로필 및 사용량 상태 모델.
 *
 * @property profile 계정 메타데이터 (ID, 별칭, 이메일, 정렬 순서, 독립 세션 프로필명)
 * @property usageState 현재 사용량 조회 상태 (Loading, AuthRequired, Success, Error)
 * @property cachedSuccess DataStore에 영구 보관된 마지막 성공 스냅샷 (Warm Start 즉시 표시용)
 * @property isRefreshing 해당 계정의 백그라운드 새로고침 진행 여부
 */
data class GeminiAccountUsageEntry(
    val profile: GeminiAccountProfile,
    val usageState: GeminiUsageState = GeminiUsageState.Loading,
    val cachedSuccess: GeminiUsageState.Success? = null,
    val isRefreshing: Boolean = false
) {
    /**
     * 화면 깜빡임(Flicker) 없이 즉시 표시할 수 있는 사용량 성공 스냅샷.
     * 현재 상태가 Success이면 이를 우선 사용하고, 로딩/만료/오류 중에도 이전 캐시가 있으면 유지합니다.
     */
    val displayedSuccess: GeminiUsageState.Success?
        get() = (usageState as? GeminiUsageState.Success) ?: cachedSuccess

    /** 메인 요약 버튼에 표시할 대표 사용률(%) (5시간 단기 한도 만료 보정 기준) */
    val summaryPercent: Int?
        get() = summaryPercent()

    fun summaryPercent(nowMillis: Long = System.currentTimeMillis()): Int? {
        if (displayedSuccess == null) return null
        return effectiveFiveHourPercent(nowMillis)
    }

    /** 5시간 단기 한도 경고 단계 (만료 보정 반영) */
    val fiveHourWarningLevel: GeminiUsageWarningLevel
        get() = fiveHourWarningLevel()

    fun fiveHourWarningLevel(nowMillis: Long = System.currentTimeMillis()): GeminiUsageWarningLevel {
        if (displayedSuccess == null) return GeminiUsageWarningLevel.NORMAL
        return GeminiUsageWarningLevel.fromPercent(effectiveFiveHourPercent(nowMillis))
    }

    /** 주간 누적 한도 경고 단계 (만료 보정 반영) */
    val weeklyWarningLevel: GeminiUsageWarningLevel
        get() = weeklyWarningLevel()

    fun weeklyWarningLevel(nowMillis: Long = System.currentTimeMillis()): GeminiUsageWarningLevel {
        if (displayedSuccess == null) return GeminiUsageWarningLevel.NORMAL
        return GeminiUsageWarningLevel.fromPercent(effectiveWeeklyPercent(nowMillis))
    }

    /**
     * 계정의 종합 경고 단계 (만료 보정 반영).
     * 5시간 단기 한도와 주간 누적 한도 중 더 높은 위험도를 반환합니다.
     */
    val overallWarningLevel: GeminiUsageWarningLevel
        get() = overallWarningLevel()

    fun overallWarningLevel(nowMillis: Long = System.currentTimeMillis()): GeminiUsageWarningLevel {
        if (displayedSuccess == null) return GeminiUsageWarningLevel.NORMAL
        return GeminiUsageWarningLevel.fromPercent(
            maxOf(effectiveFiveHourPercent(nowMillis), effectiveWeeklyPercent(nowMillis))
        )
    }

    /** 요약 버튼의 대표 퍼센트 기준 경고 단계 (5시간 한도 만료 보정 기준, 70% 주의, 90% 경고) */
    val summaryWarningLevel: GeminiUsageWarningLevel
        get() = summaryWarningLevel()

    fun summaryWarningLevel(nowMillis: Long = System.currentTimeMillis()): GeminiUsageWarningLevel {
        return fiveHourWarningLevel(nowMillis)
    }

    /**
     * 5시간 단기 한도 리셋 시각이 경과하여 만료되었는지 여부.
     * - 마지막 갱신(`updatedAt`) 후 5시간 이상 경과했거나(기본 문구 포함),
     * - 명시된 초기화 시각이 이미 지났다면(`remaining <= 0L`) 만료된 것으로 판별합니다.
     */
    fun isFiveHourWindowExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val success = displayedSuccess ?: return false
        if (success.updatedAt > 0L && nowMillis >= success.updatedAt &&
            nowMillis - success.updatedAt >= GeminiUsageParser.FIVE_HOUR_WINDOW_MILLIS
        ) {
            return true
        }
        val remaining = GeminiUsageParser.computeRemainingResetMillis(
            rawText = success.fiveHourResetText,
            nowMillis = nowMillis,
            updatedAt = success.updatedAt,
            isFiveHourWindow = true
        )
        return remaining != null && remaining <= 0L
    }

    /**
     * 주간 누적 한도 리셋 시각이 경과하여 만료되었는지 여부.
     * - 마지막 갱신(`updatedAt`) 후 7일 이상 경과했거나,
     * - 명시된 주간 초기화 일시가 이미 지났다면(`remaining <= 0L`) 만료된 것으로 판별합니다.
     */
    fun isWeeklyWindowExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val success = displayedSuccess ?: return false
        if (success.updatedAt > 0L && nowMillis >= success.updatedAt &&
            nowMillis - success.updatedAt >= GeminiUsageParser.WEEKLY_WINDOW_MILLIS
        ) {
            return true
        }
        val remaining = GeminiUsageParser.computeRemainingResetMillis(
            rawText = success.weeklyResetText,
            nowMillis = nowMillis,
            updatedAt = success.updatedAt,
            isFiveHourWindow = false
        )
        return remaining != null && remaining <= 0L
    }

    /** 5시간 리셋 만료를 반영한 유효 퍼센트 (만료 시 0%) */
    fun effectiveFiveHourPercent(nowMillis: Long = System.currentTimeMillis()): Int {
        val success = displayedSuccess ?: return 0
        return if (isFiveHourWindowExpired(nowMillis)) 0 else success.fiveHourPercent.coerceIn(0, 100)
    }

    /** 주간 리셋 만료를 반영한 유효 퍼센트 (만료 시 0%) */
    fun effectiveWeeklyPercent(nowMillis: Long = System.currentTimeMillis()): Int {
        val success = displayedSuccess ?: return 0
        return if (isWeeklyWindowExpired(nowMillis)) 0 else success.weeklyPercent.coerceIn(0, 100)
    }

    /**
     * 5시간 타이머 미시작 여부 판별.
     * 구체적인 초기화 시각이 없거나(remaining == null), 초기화 시각이 이미 지났으면(remaining <= 0L) true!
     * 단, fiveHourPercent == 0이라도 미래의 초기화 시각(remaining > 0L)이 명시되어 있으면 타이머가 진행 중이므로 false를 반환합니다.
     */
    fun isFiveHourTimerUnstarted(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val success = displayedSuccess ?: return false
        if (isFiveHourWindowExpired(nowMillis)) return true
        val remaining = GeminiUsageParser.computeRemainingResetMillis(
            rawText = success.fiveHourResetText,
            nowMillis = nowMillis,
            updatedAt = success.updatedAt,
            isFiveHourWindow = true
        )
        return remaining == null || remaining <= 0L
    }

    /** 할당량 소진 사유 (주간 소진 우선, 5시간 소진 차순, 수동 내림) */
    fun quotaExhaustionReason(nowMillis: Long = System.currentTimeMillis()): GeminiQuotaExhaustionReason? {
        val isManual = profile.isManuallyExhausted(nowMillis)
        if (displayedSuccess == null) {
            return if (isManual) GeminiQuotaExhaustionReason.MANUAL_EXHAUSTED else null
        }
        val fiveHourPct = effectiveFiveHourPercent(nowMillis)
        val weeklyPct = effectiveWeeklyPercent(nowMillis)
        return when {
            weeklyPct >= 100 -> GeminiQuotaExhaustionReason.WEEKLY_EXHAUSTED
            fiveHourPct >= 100 -> GeminiQuotaExhaustionReason.FIVE_HOUR_EXHAUSTED
            isManual -> GeminiQuotaExhaustionReason.MANUAL_EXHAUSTED
            else -> null
        }
    }

    /** 할당량이 소진되어 현재 사용할 수 없는 상태인지 여부 */
    fun isQuotaExhausted(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return quotaExhaustionReason(nowMillis) != null
    }

    /** 5시간 단기 한도 리셋까지 남은 시간(밀리초, 0 이하이거나 만료된 경우 null) */
    fun remainingMillisUntilFiveHourReset(nowMillis: Long = System.currentTimeMillis()): Long? {
        val success = displayedSuccess ?: return null
        if (isFiveHourWindowExpired(nowMillis)) return null
        return GeminiUsageParser.computeRemainingResetMillis(
            rawText = success.fiveHourResetText,
            nowMillis = nowMillis,
            updatedAt = success.updatedAt,
            isFiveHourWindow = true
        )?.takeIf { it > 0L }
    }

    /**
     * 다음 5시간 단기 한도 리셋까지 남은 시간(밀리초, 롤오버 반영).
     * - 구체적인 리셋 시각 단서가 없는 미시작 계정("5시간 주기")은 null을 반환합니다.
     * - 이미 리셋 시각이 지난 계정은 다음 롤오버 주기(5시간 후)까지 남은 시간을 반환합니다.
     */
    fun nextFiveHourResetRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
        val success = displayedSuccess ?: return null
        return GeminiUsageParser.computeNextResetRemainingMillis(
            rawText = success.fiveHourResetText,
            nowMillis = nowMillis,
            updatedAt = success.updatedAt,
            isFiveHourWindow = true
        )
    }

    /**
     * 소진된 계정이 실제로 다시 사용 가능해지기까지 남은 시간(밀리초).
     * - FIVE_HOUR_EXHAUSTED / MANUAL_EXHAUSTED: 5시간 리셋까지 남은 시간
     * - WEEKLY_EXHAUSTED: 주간 리셋과 5시간 리셋 중 더 늦게 풀리는 시간
     */
    fun remainingMillisUntilUsable(nowMillis: Long = System.currentTimeMillis()): Long? {
        val success = displayedSuccess ?: return null
        val reason = quotaExhaustionReason(nowMillis) ?: return null
        return when (reason) {
            GeminiQuotaExhaustionReason.FIVE_HOUR_EXHAUSTED,
            GeminiQuotaExhaustionReason.MANUAL_EXHAUSTED -> {
                remainingMillisUntilFiveHourReset(nowMillis)
            }
            GeminiQuotaExhaustionReason.WEEKLY_EXHAUSTED -> {
                val weeklyRemaining = GeminiUsageParser.computeRemainingResetMillis(
                    rawText = success.weeklyResetText,
                    nowMillis = nowMillis,
                    updatedAt = success.updatedAt,
                    isFiveHourWindow = false
                )?.takeIf { it > 0L }
                val fiveHourRemaining = remainingMillisUntilFiveHourReset(nowMillis)
                if (weeklyRemaining != null) {
                    maxOf(weeklyRemaining, fiveHourRemaining ?: 0L)
                } else {
                    null
                }
            }
        }
    }
}

/**
 * 다중 계정(5개 이상) 제미나이 사용량 모니터링 전체 도메인 상태 모델.
 *
 * @property accounts 정렬 순서(sortOrder)대로 정렬된 계정별 사용량 목록
 * @property selectedAccountId 메인 화면 요약 버튼에 표시할 대표 계정 ID (미지정 시 첫 번째 계정)
 * @property isRefreshingAll 전체 계정 일괄 새로고침 진행 여부
 * @property isInitialized 로컬 DataStore 초기 로드(Warm Start) 완료 여부
 */
data class MultiGeminiUsageState(
    val accounts: List<GeminiAccountUsageEntry> = emptyList(),
    val selectedAccountId: String? = null,
    val isRefreshingAll: Boolean = false,
    val isInitialized: Boolean = false
) {
    /** 등록된 계정이 없는 빈 상태 여부 */
    val isEmpty: Boolean
        get() = accounts.isEmpty()

    /** 등록된 총 계정 수 */
    val accountCount: Int
        get() = accounts.size

    /** 메인 그룹에 속한 계정 목록 */
    val mainAccounts: List<GeminiAccountUsageEntry>
        get() = accounts.filter { it.profile.group == GeminiAccountGroup.MAIN }

    /** 서브 그룹에 속한 계정 목록 */
    val subAccounts: List<GeminiAccountUsageEntry>
        get() = accounts.filter { it.profile.group == GeminiAccountGroup.SUB }

    /** 메인 그룹에 속한 총 계정 수 */
    val mainAccountCount: Int
        get() = mainAccounts.size

    /** 서브 그룹에 속한 총 계정 수 */
    val subAccountCount: Int
        get() = subAccounts.size

    /** 특정 그룹에 속한 계정 수 반환 */
    fun accountCountForGroup(group: GeminiAccountGroup): Int =
        accounts.count { it.profile.group == group }

    /** 특정 그룹에 속한 계정이 비어 있는지 여부 */
    fun isGroupEmpty(group: GeminiAccountGroup): Boolean =
        accountCountForGroup(group) == 0

    /** 하나 이상의 계정이 현재 새로고침 중인지 여부 */
    val isAnyRefreshing: Boolean
        get() = isRefreshingAll || accounts.any { it.isRefreshing }

    /**
     * 메인 화면 요약 버튼에 표시할 대표/현재 계정 항목.
     * [selectedAccountId]와 일치하는 계정을 우선 반환하며, 없으면 첫 번째 계정을 반환합니다.
     */
    val representativeAccount: GeminiAccountUsageEntry?
        get() = accounts.firstOrNull { it.profile.id == selectedAccountId }
            ?: accounts.firstOrNull()

    /**
     * 메인 화면 요약 버튼에 적용할 포인트 컬러 경고 단계.
     * - 70% 이상: 주의 (CAUTION - 주황색)
     * - 90% 이상: 경고 (WARNING - 빨간색)
     */
    val summaryWarningLevel: GeminiUsageWarningLevel
        get() = summaryWarningLevel()

    fun summaryWarningLevel(nowMillis: Long = System.currentTimeMillis()): GeminiUsageWarningLevel =
        representativeAccount?.summaryWarningLevel(nowMillis) ?: GeminiUsageWarningLevel.NORMAL

    /**
     * 메인 화면 요약 바 좌측에 표시할 대표 계정 라벨.
     * 예: `joa31 ▾` (`Gemini` 글자 및 대괄호 제거)
     */
    val summaryAccountLabel: String
        get() {
            val account = representativeAccount ?: return "계정 추가 ▾"
            return "${account.profile.displayAlias} ▾"
        }

    /**
     * 메인 화면 요약 바에 분리된 컬러 배지로 표시할 퍼센트/상태 텍스트.
     * 예: `27%`, `로그인 필요`, `조회중`
     */
    val summaryBadgeText: String?
        get() = summaryBadgeText()

    fun summaryBadgeText(nowMillis: Long = System.currentTimeMillis()): String? {
        val account = representativeAccount ?: return null
        val success = account.displayedSuccess
        return when {
            success != null -> "${account.effectiveFiveHourPercent(nowMillis)}%"
            account.usageState is GeminiUsageState.AuthRequired -> "로그인 필요"
            account.usageState is GeminiUsageState.Loading || account.isRefreshing -> "조회중"
            else -> "--%"
        }
    }

    /**
     * 메인 화면 요약 바에 퍼센트 배지 옆에 함께 표시할 단기 한도 초기화 시각.
     * 만료되었거나 타이머 미시작인 경우에는 과거 시각을 숨깁니다.
     */
    val summaryResetTimeText: String?
        get() = summaryResetTimeText()

    fun summaryResetTimeText(nowMillis: Long = System.currentTimeMillis()): String? {
        val account = representativeAccount ?: return null
        val success = account.displayedSuccess ?: return null
        if (account.isFiveHourWindowExpired(nowMillis) || account.isFiveHourTimerUnstarted(nowMillis)) {
            return null
        }
        return GeminiUsageParser.formatShortResetTime(success.fiveHourResetText)
            .takeIf { it.isNotBlank() }
    }

    /**
     * 메인 화면 요약 바의 접근성/요약 문자열 (`Gemini` 접두사 제외).
     * 예: `본계정 42% ▾`
     */
    val summaryButtonText: String
        get() = summaryButtonText()

    fun summaryButtonText(nowMillis: Long = System.currentTimeMillis()): String {
        val account = representativeAccount ?: return "계정 추가 ▾"
        val alias = account.profile.displayAlias
        val badge = summaryBadgeText(nowMillis) ?: return "$alias ▾"
        return "$alias $badge ▾"
    }

    /**
     * 특정 그룹(메인 또는 서브)에 속한 즉시 사용 가능/확인 중 계정 목록을 최적 사용 순서로 정렬합니다.
     */
    fun availableAccountsSorted(
        group: GeminiAccountGroup,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GeminiAccountUsageEntry> = availableAccountsSortedInternal(group, nowMillis)

    /**
     * 전체 그룹의 즉시 사용 가능/확인 중 계정 목록을 최적 사용 순서로 정렬합니다.
     */
    fun availableAccountsSorted(
        nowMillis: Long = System.currentTimeMillis()
    ): List<GeminiAccountUsageEntry> = availableAccountsSortedInternal(null, nowMillis)

    private fun availableAccountsSortedInternal(
        group: GeminiAccountGroup?,
        nowMillis: Long
    ): List<GeminiAccountUsageEntry> {
        val baseAccounts = if (group == null) accounts else accounts.filter { it.profile.group == group }
        val usableAccounts = baseAccounts.filter { it.displayedSuccess != null && !it.isQuotaExhausted(nowMillis) }
        val unconfirmedAccounts = baseAccounts.filter { it.displayedSuccess == null }

        val sortedUsable = usableAccounts.sortedWith { a, b ->
            val remA = a.nextFiveHourResetRemainingMillis(nowMillis)
            val remB = b.nextFiveHourResetRemainingMillis(nowMillis)

            val aHasTimer = remA != null
            val bHasTimer = remB != null

            when {
                // 1) 둘 다 타이머가 있는 경우: 다음 초기화까지 남은 시간이 짧은 순 (진정한 5시간 초기화 임박 순!)
                aHasTimer && bHasTimer -> {
                    val remCmp = remA!!.compareTo(remB!!)
                    if (remCmp != 0) return@sortedWith remCmp

                    val fiveCmp = a.effectiveFiveHourPercent(nowMillis).compareTo(b.effectiveFiveHourPercent(nowMillis))
                    if (fiveCmp != 0) return@sortedWith fiveCmp

                    val weeklyCmp = a.effectiveWeeklyPercent(nowMillis).compareTo(b.effectiveWeeklyPercent(nowMillis))
                    if (weeklyCmp != 0) return@sortedWith weeklyCmp

                    a.profile.sortOrder.compareTo(b.profile.sortOrder)
                }
                // 2) 타이머가 진행 중/롤오버된 계정이 타이머가 없는 미시작 계정보다 앞 (곧 리셋될 쿼터 먼저 소진)
                aHasTimer && !bHasTimer -> -1
                !aHasTimer && bHasTimer -> 1
                // 3) 둘 다 타이머가 없는 미시작 계정 ("5시간 주기"): 주간 누적 사용량 오름차순
                else -> {
                    val weeklyCmp = a.effectiveWeeklyPercent(nowMillis).compareTo(b.effectiveWeeklyPercent(nowMillis))
                    if (weeklyCmp != 0) weeklyCmp else a.profile.sortOrder.compareTo(b.profile.sortOrder)
                }
            }
        }

        val sortedUnconfirmed = unconfirmedAccounts.sortedBy { it.profile.sortOrder }

        return sortedUsable + sortedUnconfirmed
    }

    /**
     * 특정 그룹(메인 또는 서브)에 속한 할당량 소진 계정 목록을 해제 임박 순서로 정렬합니다.
     */
    fun exhaustedAccountsSorted(
        group: GeminiAccountGroup,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GeminiAccountUsageEntry> = exhaustedAccountsSortedInternal(group, nowMillis)

    /**
     * 전체 그룹의 할당량 소진 계정 목록을 해제 임박 순서로 정렬합니다.
     */
    fun exhaustedAccountsSorted(
        nowMillis: Long = System.currentTimeMillis()
    ): List<GeminiAccountUsageEntry> = exhaustedAccountsSortedInternal(null, nowMillis)

    private fun exhaustedAccountsSortedInternal(
        group: GeminiAccountGroup?,
        nowMillis: Long
    ): List<GeminiAccountUsageEntry> {
        val baseAccounts = if (group == null) accounts else accounts.filter { it.profile.group == group }
        val exhausted = baseAccounts.filter { it.isQuotaExhausted(nowMillis) }
        return exhausted.sortedWith { a, b ->
            val reasonA = a.quotaExhaustionReason(nowMillis)
            val reasonB = b.quotaExhaustionReason(nowMillis)
            val usableA = a.remainingMillisUntilUsable(nowMillis)
                ?: if (reasonA == GeminiQuotaExhaustionReason.WEEKLY_EXHAUSTED) Long.MAX_VALUE else (5L * 3600_000L)
            val usableB = b.remainingMillisUntilUsable(nowMillis)
                ?: if (reasonB == GeminiQuotaExhaustionReason.WEEKLY_EXHAUSTED) Long.MAX_VALUE else (5L * 3600_000L)

            val usableCmp = usableA.compareTo(usableB)
            if (usableCmp != 0) return@sortedWith usableCmp

            val reasonCmp = (reasonA?.ordinal ?: 0).compareTo(reasonB?.ordinal ?: 0)
            if (reasonCmp != 0) return@sortedWith reasonCmp

            val weeklyCmp = a.effectiveWeeklyPercent(nowMillis).compareTo(b.effectiveWeeklyPercent(nowMillis))
            if (weeklyCmp != 0) return@sortedWith weeklyCmp

            a.profile.sortOrder.compareTo(b.profile.sortOrder)
        }
    }

    /**
     * 특정 그룹(메인 또는 서브)에 속한 전체 계정을 최적 사용 순서로 정렬합니다.
     */
    fun sortedAccountsForUsage(
        group: GeminiAccountGroup,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GeminiAccountUsageEntry> = availableAccountsSorted(group, nowMillis) + exhaustedAccountsSorted(group, nowMillis)

    /**
     * 전체 계정을 최적 사용 순서로 정렬합니다.
     */
    fun sortedAccountsForUsage(
        nowMillis: Long = System.currentTimeMillis()
    ): List<GeminiAccountUsageEntry> = availableAccountsSorted(nowMillis) + exhaustedAccountsSorted(nowMillis)
}
