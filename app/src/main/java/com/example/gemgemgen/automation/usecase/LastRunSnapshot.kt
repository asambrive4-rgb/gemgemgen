package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.RepeatCountParser

data class LastRunSnapshot(
    val promptTemplate: String,
    val repeatCountText: String,
    val targetApp: AutomationTargetApp
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
            targetApp = snapshot.targetApp
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

