package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotRepository
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RecordAutomationStartUseCase
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordAutomationStartUseCaseTest {
    @Test
    fun record_savesLastRunSnapshotAndCopiesOriginalPrompt() = runBlocking {
        val repository = RecordingLastRunSnapshotRepository()
        val clipboard = RecordingClipboardGateway()
        val useCase = RecordAutomationStartUseCase(
            lastRunSnapshotStore = LastRunSnapshotStore(repository),
            clipboardGateway = clipboard,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )

        useCase.record(
            AutomationRunRequest(
                promptTemplate = "base __hair__ prompt",
                repeatCountText = "7",
                targetApp = AutomationTargetApp.CHATGPT
            )
        )

        assertEquals(
            LastRunSnapshot("base __hair__ prompt", "7", AutomationTargetApp.CHATGPT),
            repository.savedSnapshot
        )
        assertEquals("base __hair__ prompt", clipboard.writtenText)
    }

    private class RecordingLastRunSnapshotRepository : LastRunSnapshotRepository {
        var savedSnapshot: LastRunSnapshot? = null

        override fun load(): LastRunSnapshot? = savedSnapshot

        override fun save(snapshot: LastRunSnapshot) {
            savedSnapshot = snapshot
        }
    }

    private class RecordingClipboardGateway : ClipboardGateway {
        var writtenText = ""

        override fun readText(): String = ""

        override fun writeText(text: String) {
            writtenText = text
        }
    }
}
