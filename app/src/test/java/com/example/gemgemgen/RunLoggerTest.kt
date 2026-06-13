package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Test

class RunLoggerTest {
    @Test
    fun append_savesRecentLogsNewestFirstAndKeepsTenEntries() {
        val storage = FakeRunLogStorage()
        val logger = RunLogger(storage)

        for (index in 1..12) {
            logger.append(
                AutomationRunLog(
                    startedAtMillis = index.toLong(),
                    finishedAtMillis = index.toLong() + 100,
                    status = AutomationRunLogStatus.SUCCESS,
                    lastStep = "step $index",
                    message = "message $index",
                    imeRestoreMessage = "성공"
                )
            )
        }

        val logs = logger.loadRecent()
        assertEquals(10, logs.size)
        assertEquals("step 12", logs.first().lastStep)
        assertEquals("step 3", logs.last().lastStep)
    }

    @Test
    fun fromState_mapsTerminalStatesToLogStatus() {
        assertEquals(
            AutomationRunLogStatus.SUCCESS,
            AutomationRunLogStatus.fromState(AutomationRunState.Success)
        )
        assertEquals(
            AutomationRunLogStatus.STOPPED,
            AutomationRunLogStatus.fromState(AutomationRunState.Stopped)
        )
        assertEquals(
            AutomationRunLogStatus.FAILURE,
            AutomationRunLogStatus.fromState(AutomationRunState.Failure("실패"))
        )
    }

    private class FakeRunLogStorage : RunLogStorage {
        private var value: String = ""

        override fun read(): String = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
