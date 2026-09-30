// 역할: 앱 복원 시 사용할 마지막 자동화 실행 설정 스냅샷 데이터 모델과 저장소를 정의합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.core.AppDefaults

data class LastRunSnapshot(
    val promptTemplate: String,
    val repeatCountText: String,
    val targetApp: AutomationTargetApp,
    val flowImageCount: Int = AppDefaults.DEFAULT_FLOW_IMAGE_COUNT
) {
    fun hasRestorablePrompt(): Boolean =
        promptTemplate.isNotBlank() || repeatCountText.isNotBlank()
}

interface LastRunSnapshotRepository {
    fun load(): LastRunSnapshot?
    fun save(snapshot: LastRunSnapshot)
}

class LastRunSnapshotStore(
    private val repository: LastRunSnapshotRepository
) {
    fun load(): LastRunSnapshot? {
        val snapshot = repository.load() ?: return null
        if (!snapshot.hasRestorablePrompt()) return null

        return snapshot.copy(
            repeatCountText = RepeatCountParser.normalizeInput(snapshot.repeatCountText)
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