package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.RepeatCountParser

data class LastRunSnapshot(
    val promptTemplate: String,
    val repeatCountText: String,
    val targetApp: AutomationTargetApp
)

class LastRunSnapshotStore(
    private val storage: LastRunSnapshotStorage
) {
    fun load(): LastRunSnapshot? {
        val promptTemplate = storage.readPromptTemplate()
        val repeatCountText = storage.readRepeatCountText()
        if (promptTemplate.isBlank() && repeatCountText.isBlank()) return null

        return LastRunSnapshot(
            promptTemplate = promptTemplate,
            repeatCountText = RepeatCountParser.normalizeInput(repeatCountText),
            targetApp = AutomationTargetApp.fromStorageValue(storage.readTargetApp())
        )
    }

    fun save(snapshot: LastRunSnapshot) {
        storage.write(
            promptTemplate = snapshot.promptTemplate,
            repeatCountText = RepeatCountParser.normalizeInput(snapshot.repeatCountText),
            targetApp = snapshot.targetApp.storageValue
        )
    }
}

interface LastRunSnapshotStorage {
    fun readPromptTemplate(): String
    fun readRepeatCountText(): String
    fun readTargetApp(): String
    fun write(promptTemplate: String, repeatCountText: String, targetApp: String)
}

