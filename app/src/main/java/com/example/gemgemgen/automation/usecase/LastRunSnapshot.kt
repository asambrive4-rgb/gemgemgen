// 역할: 앱 복원 시 사용할 마지막 자동화 실행 설정 스냅샷 모델을 정의합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.core.AppDefaults

data class LastRunSnapshot(
    val promptTemplate: String,
    val repeatCountText: String,
    val targetApp: AutomationTargetApp,
    val flowImageCount: Int = AppDefaults.DEFAULT_FLOW_IMAGE_COUNT
)

class LastRunSnapshotStore(
    private val repository: LastRunSnapshotRepository
) {
    fun load(): LastRunSnapshot? {
        val snapshot = repository.load() ?: return null
        val promptTemplate = snapshot.promptTemplate
        val repeatCountText = snapshot.repeatCountText
        if (promptTemplate.isBlank() && repeatCountText.isBlank()) return null

        return LastRunSnapshot(
            promptTemplate = promptTemplate,
            repeatCountText = RepeatCountParser.normalizeInput(repeatCountText),
            targetApp = snapshot.targetApp,
            flowImageCount = snapshot.flowImageCount
        )
    }

    fun save(snapshot: LastRunSnapshot) {
        repository.save(
            snapshot.copy(
                repeatCountText = RepeatCountParser.normalizeInput(snapshot.repeatCountText)
            )
        )
    }
}

interface LastRunSnapshotRepository {
    fun load(): LastRunSnapshot?
    fun save(snapshot: LastRunSnapshot)
}