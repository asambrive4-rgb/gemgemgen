// 역할: 다중 제미나이 계정의 개별 사용량 항목과 전체 목록 상태, 대표 계정 요약 버튼 문구 및 경고 단계 계산 규칙을 정의합니다.
package com.example.gemgemgen.usage.domain

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

    /** 메인 요약 버튼에 표시할 대표 사용률(%) (5시간 단기 한도 기준) */
    val summaryPercent: Int?
        get() = displayedSuccess?.fiveHourPercent?.coerceIn(0, 100)

    /** 5시간 단기 한도 경고 단계 */
    val fiveHourWarningLevel: GeminiUsageWarningLevel
        get() = displayedSuccess?.fiveHourWarningLevel ?: GeminiUsageWarningLevel.NORMAL

    /** 주간 누적 한도 경고 단계 */
    val weeklyWarningLevel: GeminiUsageWarningLevel
        get() = displayedSuccess?.weeklyWarningLevel ?: GeminiUsageWarningLevel.NORMAL

    /**
     * 계정의 종합 경고 단계.
     * 5시간 단기 한도와 주간 누적 한도 중 더 높은 위험도를 반환합니다.
     */
    val overallWarningLevel: GeminiUsageWarningLevel
        get() = displayedSuccess?.overallWarningLevel ?: GeminiUsageWarningLevel.NORMAL

    /** 요약 버튼의 대표 퍼센트 기준 경고 단계 (70% 주의, 90% 경고) */
    val summaryWarningLevel: GeminiUsageWarningLevel
        get() {
            val success = displayedSuccess ?: return GeminiUsageWarningLevel.NORMAL
            return GeminiUsageWarningLevel.fromPercent(
                maxOf(success.fiveHourPercent, success.weeklyPercent)
            )
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
        get() = representativeAccount?.summaryWarningLevel ?: GeminiUsageWarningLevel.NORMAL

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
        get() {
            val account = representativeAccount ?: return null
            val success = account.displayedSuccess
            return when {
                success != null -> "${success.fiveHourPercent.coerceIn(0, 100)}%"
                account.usageState is GeminiUsageState.AuthRequired -> "로그인 필요"
                account.usageState is GeminiUsageState.Loading || account.isRefreshing -> "조회중"
                else -> "--%"
            }
        }

    /**
     * 메인 화면 요약 바에 퍼센트 배지 옆에 함께 표시할 단기 한도 초기화 시각.
     * 예: `오후 2:00`
     */
    val summaryResetTimeText: String?
        get() {
            val success = representativeAccount?.displayedSuccess ?: return null
            return GeminiUsageParser.formatShortResetTime(success.fiveHourResetText)
                .takeIf { it.isNotBlank() }
        }

    /**
     * 메인 화면 요약 바의 접근성/요약 문자열 (`Gemini` 접두사 제외).
     * 예: `본계정 42%`
     */
    val summaryButtonText: String
        get() {
            val account = representativeAccount ?: return "계정 추가 ▾"
            val alias = account.profile.displayAlias
            val badge = summaryBadgeText ?: return "$alias ▾"
            return "$alias $badge ▾"
        }
}
