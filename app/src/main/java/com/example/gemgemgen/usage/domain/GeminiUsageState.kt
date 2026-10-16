// 역할: Google AI Pro 제미나이 사용량 조회 상태(로딩, 로그인 필요, 성공, 오류)와 경고 단계 모델을 정의합니다.
package com.example.gemgemgen.usage.domain

/**
 * Google AI Pro 계정의 제미나이 사용량 한도 조회 상태 모델.
 */
sealed interface GeminiUsageState {
    /** 초기 로딩 중 (캐시된 데이터가 없고 웹뷰 조회가 진행 중인 상태) */
    data object Loading : GeminiUsageState

    /** 구글 계정 세션 만료 또는 미로그인 상태 (accounts.google.com 리다이렉트 감지) */
    data object AuthRequired : GeminiUsageState

    /**
     * 사용량 한도 파싱 성공 상태.
     *
     * @property fiveHourPercent 5시간 단기 한도 사용률 (0~100)
     * @property fiveHourResetText 5시간 단기 한도 리셋 카운트다운/시각 안내 문구
     * @property weeklyPercent 주간 누적 한도 사용률 (0~100)
     * @property weeklyResetText 주간 누적 한도 리셋 시각 안내 문구
     * @property updatedAt 마지막 갱신 시각 (epoch millis)
     */
    data class Success(
        val fiveHourPercent: Int,
        val fiveHourResetText: String,
        val weeklyPercent: Int,
        val weeklyResetText: String,
        val updatedAt: Long
    ) : GeminiUsageState {
        val fiveHourWarningLevel: GeminiUsageWarningLevel
            get() = GeminiUsageWarningLevel.fromPercent(fiveHourPercent)

        val weeklyWarningLevel: GeminiUsageWarningLevel
            get() = GeminiUsageWarningLevel.fromPercent(weeklyPercent)

        /** 5시간 단기 한도와 주간 누적 한도 중 더 높은 사용률(%) */
        val peakPercent: Int
            get() = maxOf(fiveHourPercent, weeklyPercent).coerceIn(0, 100)

        /** 두 한도 중 더 높은 위험 단계를 반환합니다. */
        val overallWarningLevel: GeminiUsageWarningLevel
            get() = GeminiUsageWarningLevel.fromPercent(peakPercent)
    }

    /** 네트워크 오류 또는 파싱 타임아웃 등 에러 상태 */
    data class Error(val message: String) : GeminiUsageState
}

/**
 * 사용량 퍼센트 구간별 시각적 경고 단계.
 * - 70% 미만: 일반 (NORMAL)
 * - 70% 이상 ~ 90% 미만: 주의 (CAUTION)
 * - 90% 이상: 경고 (WARNING)
 */
enum class GeminiUsageWarningLevel {
    NORMAL,
    CAUTION,
    WARNING;

    companion object {
        const val CAUTION_THRESHOLD_PERCENT = 70
        const val WARNING_THRESHOLD_PERCENT = 90

        fun fromPercent(percent: Int): GeminiUsageWarningLevel {
            return when {
                percent >= WARNING_THRESHOLD_PERCENT -> WARNING
                percent >= CAUTION_THRESHOLD_PERCENT -> CAUTION
                else -> NORMAL
            }
        }
    }
}
