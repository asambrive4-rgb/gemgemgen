package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationRetryWaitPolicyTest {
    @Test
    fun nextDelayMillis_retriesEvery250msDuringFirst3Seconds() {
        assertEquals(250L, AutomationRetryWaitPolicy.nextDelayMillis(0L))
        assertEquals(250L, AutomationRetryWaitPolicy.nextDelayMillis(2750L))
    }

    @Test
    fun nextDelayMillis_retriesEvery1000msAfterFirst3Seconds() {
        assertEquals(1000L, AutomationRetryWaitPolicy.nextDelayMillis(3000L))
        assertEquals(1000L, AutomationRetryWaitPolicy.nextDelayMillis(9000L))
    }

    @Test
    fun nextDelayMillis_stopsAfter10Seconds() {
        assertEquals(1L, AutomationRetryWaitPolicy.nextDelayMillis(9999L))
        assertNull(AutomationRetryWaitPolicy.nextDelayMillis(10000L))
    }
}
