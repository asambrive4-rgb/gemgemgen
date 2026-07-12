package com.example.gemgemgen.analysis.domain

/**
 * Grok 구독 크레딧 사용량 → 남은 비율(%) 계산.
 * used/limit 단위는 동일 단위(cent 등)면 된다.
 */
object GrokQuotaPolicy {
    fun remainingPercent(used: Long, limit: Long): Int {
        if (limit <= 0L) return 0
        val remaining = (limit - used).coerceAtLeast(0L)
        val percent = ((remaining * 100.0) / limit.toDouble()).toInt()
        return percent.coerceIn(0, 100)
    }
}
