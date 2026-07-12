package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.GrokQuotaPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class GrokQuotaPolicyTest {
    @Test
    fun remainingPercent_fullWhenUnused() {
        assertEquals(100, GrokQuotaPolicy.remainingPercent(used = 0, limit = 15_000))
    }

    @Test
    fun remainingPercent_half() {
        assertEquals(50, GrokQuotaPolicy.remainingPercent(used = 7_500, limit = 15_000))
    }

    @Test
    fun remainingPercent_zeroWhenExhausted() {
        assertEquals(0, GrokQuotaPolicy.remainingPercent(used = 15_000, limit = 15_000))
        assertEquals(0, GrokQuotaPolicy.remainingPercent(used = 20_000, limit = 15_000))
    }

    @Test
    fun remainingPercent_zeroWhenLimitInvalid() {
        assertEquals(0, GrokQuotaPolicy.remainingPercent(used = 0, limit = 0))
        assertEquals(0, GrokQuotaPolicy.remainingPercent(used = 10, limit = -1))
    }
}
