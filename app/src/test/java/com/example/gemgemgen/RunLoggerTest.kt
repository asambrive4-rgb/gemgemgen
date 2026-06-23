package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.gemgemgen.automation.android.RunLogCodec

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

    @Test
    fun append_preservesTargetApp() {
        val logger = RunLogger(FakeRunLogStorage())

        logger.append(
            AutomationRunLog(
                startedAtMillis = 1L,
                finishedAtMillis = 2L,
                status = AutomationRunLogStatus.SUCCESS,
                lastStep = "완료",
                message = "성공",
                imeRestoreMessage = "성공",
                targetApp = AutomationTargetApp.CHATGPT.storageValue
            )
        )

        assertEquals(
            AutomationTargetApp.CHATGPT.storageValue,
            logger.loadRecent().single().targetApp
        )
    }

    @Test
    fun codec_readsLegacySixAndElevenFieldLogsAndCurrentTwelveFieldLogs() {
        val log = AutomationRunLog(
            startedAtMillis = 1L,
            finishedAtMillis = 2L,
            status = AutomationRunLogStatus.SUCCESS,
            lastStep = "done",
            message = "success",
            imeRestoreMessage = "restored",
            repeatCount = 3,
            completedCount = 3,
            successCount = 3,
            markerStatus = "success",
            targetApp = AutomationTargetApp.CHATGPT.storageValue
        )
        val fields = RunLogCodec.encode(listOf(log)).split("\t")

        assertEquals("", RunLogCodec.decode(fields.take(6).joinToString("\t")).single().targetApp)
        assertEquals("", RunLogCodec.decode(fields.take(11).joinToString("\t")).single().targetApp)
        assertEquals(
            AutomationTargetApp.CHATGPT.storageValue,
            RunLogCodec.decode(fields.joinToString("\t")).single().targetApp
        )
    }

    private class FakeRunLogStorage : RunLogRepository {
        private var logs: List<AutomationRunLog> = emptyList()

        override fun load(): List<AutomationRunLog> = logs

        override fun save(logs: List<AutomationRunLog>) {
            this.logs = logs
        }
    }
}
