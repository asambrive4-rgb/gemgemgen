// 역할: 자동화 실행 시 마지막 실행 설정과 원본 프롬프트를 히스토리에 기록합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

/** 자동화를 시작한 기기에 마지막 실행값과 원본 프롬프트를 남긴다. */
fun interface AutomationHistoryRecorder {
    suspend fun record(request: AutomationRunRequest)
}

class RecordAutomationHistoryUseCase(
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val promptHistoryStore: PromptHistoryStore? = null,
    private val dispatchers: AppDispatchers = AppDispatchers()
) : AutomationHistoryRecorder {
    override suspend fun record(request: AutomationRunRequest) {
        withContext(dispatchers.io) {
            lastRunSnapshotStore.save(
                LastRunSnapshot(
                    promptTemplate = request.promptTemplate,
                    repeatCountText = request.repeatCountText,
                    targetApp = request.targetApp,
                    flowImageCount = request.flowImageCount
                )
            )
            promptHistoryStore?.record(
                prompt = request.promptTemplate,
                targetApp = request.targetApp
            )
        }
    }
}

object NoOpAutomationHistoryRecorder : AutomationHistoryRecorder {
    override suspend fun record(request: AutomationRunRequest) = Unit
}
