package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.RepeatCountParser

data class LastRunSnapshot(
    val promptTemplate: String,
    val repeatCountText: String
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
            repeatCountText = RepeatCountParser.normalizeInput(repeatCountText)
        )
    }

    fun save(snapshot: LastRunSnapshot) {
        storage.write(
            promptTemplate = snapshot.promptTemplate,
            repeatCountText = RepeatCountParser.normalizeInput(snapshot.repeatCountText)
        )
    }
}

interface LastRunSnapshotStorage {
    fun readPromptTemplate(): String
    fun readRepeatCountText(): String
    fun write(promptTemplate: String, repeatCountText: String)
}

