package com.example.gemgemgen

import com.example.gemgemgen.remote.usecase.ManageReceivedAutomationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageReceivedAutomationUseCaseTest {
    @Test
    fun acceptLatest_replacesPreviousRequestAndRejectsItsLateState() {
        val useCase = ManageReceivedAutomationUseCase()

        assertEquals(null, useCase.acceptLatest("request-a"))
        assertEquals("request-a", useCase.acceptLatest("request-b"))

        assertFalse(useCase.canPublish("request-a"))
        assertTrue(useCase.canPublish("request-b"))
        assertFalse(useCase.finishIfCurrent("request-a"))
        assertTrue(useCase.canPublish("request-b"))
    }

    @Test
    fun stopIfCurrent_onlyStopsMatchingRequest() {
        val useCase = ManageReceivedAutomationUseCase()
        useCase.acceptLatest("request-b")

        assertFalse(useCase.stopIfCurrent("request-a"))
        assertTrue(useCase.canPublish("request-b"))
        assertTrue(useCase.stopIfCurrent("request-b"))
        assertFalse(useCase.canPublish("request-b"))
    }
}
