// 역할: 자동화 실행 시 마지막 실행 설정과 원본 프롬프트 히스토리 저장을 담당하는 RecordAutomationHistoryUseCase 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotRepository
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.RecordAutomationHistoryUseCase
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordAutomationHistoryUseCaseTest {
    @Test
    fun record_savesLastRunSnapshot() = runBlocking {
        val repository = RecordingLastRunSnapshotRepository()
        val useCase = RecordAutomationHistoryUseCase(
            lastRunSnapshotStore = LastRunSnapshotStore(repository),
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
    }

    @Test
    fun record_recordsPromptHistoryWhenStoreProvided() = runBlocking {
        val repository = RecordingLastRunSnapshotRepository()
        val historyRepo = FakePromptHistoryRepository()
        val historyStore = PromptHistoryStore(historyRepo)
        val useCase = RecordAutomationHistoryUseCase(
            lastRunSnapshotStore = LastRunSnapshotStore(repository),
            promptHistoryStore = historyStore,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )

        useCase.record(
            AutomationRunRequest(
                promptTemplate = "test history prompt",
                repeatCountText = "3",
                targetApp = AutomationTargetApp.GEMINI
            )
        )

        val historyItems = historyStore.load()
        assertEquals(1, historyItems.size)
        assertEquals("test history prompt", historyItems[0].prompt)
    }

    private class RecordingLastRunSnapshotRepository : LastRunSnapshotRepository {
        var savedSnapshot: LastRunSnapshot? = null

        override fun load(): LastRunSnapshot? = savedSnapshot

        override fun save(snapshot: LastRunSnapshot) {
            savedSnapshot = snapshot
        }
    }
}
