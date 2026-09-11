// 역할: 자동화 실행 실패나 노드 미발견 시 대기 시간과 재시도 정책을 검토하고 결정합니다.
package com.example.gemgemgen.automation.domain

object AutomationRetryWaitPolicy {
    private const val FAST_RETRY_WINDOW_MS = 3000L
    private const val TOTAL_RETRY_WINDOW_MS = 10000L
    private const val FAST_RETRY_WAIT_MS = 250L
    private const val SLOW_RETRY_WAIT_MS = 1000L

    fun nextDelayMillis(elapsedMillis: Long): Long? {
        if (elapsedMillis >= TOTAL_RETRY_WINDOW_MS) return null

        val retryWaitMillis = if (elapsedMillis < FAST_RETRY_WINDOW_MS) {
            FAST_RETRY_WAIT_MS
        } else {
            SLOW_RETRY_WAIT_MS
        }

        return minOf(retryWaitMillis, TOTAL_RETRY_WINDOW_MS - elapsedMillis)
    }
}

